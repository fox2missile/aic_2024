import redis

import os
import random
import sys
import time

from datetime import datetime
from collections import Counter

from redis import lock

from test_utils import *

SERVER_ID_KEY = "server_id"

random.seed(69)

MATCH_SEEDS = [1234, 1111, 2222, 3333, 4444, 5555, 6969]


class TestServer:
    def __init__(self, test_players: List[str], map_coverage: int, main_player: str = 'kyuu', repeat: int = 1):
        self.redis = redis.Redis(host='localhost', port=6379, db=0)
        self.lock_queue = lock.Lock(self.redis, QUEUE_LOCK_KEY)
        self.id = datetime.now().strftime("%Y-%m-%d %H:%M")
        self.lock_result = lock.Lock(self.redis, get_result_lock_key(self.id))
        self.repeat = repeat
        all_maps = [
            '3vs3',
            'AlmostClosed',
            'Arcs',
            'Bands',
            'Basic1',
            'Basic2',
            'Basic3',
            'BasicBig',
            'Battlefield',
            'BetterCallSpaceGames',
            'Boxes',
            'BTD7Logs',
            'Cross',
            'Crowded',
            'Electrolysis',
            'Escape',
            'ExpandingWaves',
            'FightForTheMiddle',
            'HeHeHeHa',
            'HotBorder',
            'HotMap',
            'HotSnake',
            'InsideTheCastleWalls',
            'JumpAndKill',
            'Love',
            'Mines',
            'Moniato',
            'NormalMap',
            'RealBasic',
            'Rocky',
            'SetTheory',
            'Shapes',
            'Squares',
            'SummonersRift',
            'SwimmingPool',
            'TeamBattle',
            'xdd',
        ]

        self.maps: List[str] = []
        for map_candidate in all_maps:
            if random.randint(0, 100) <= map_coverage:
                self.maps.append(map_candidate)
        # self.maps = ['maptestsmall']
        self.main_player = main_player
        self.test_players = test_players
        # self.test_players = ['kyuu_v6_1', 'kyuu_v7', 'kyuu_v8']
        # self.test_players = ['kyuu_v6_1', 'kyuu_v7', 'kyuu_v8']
        # self.test_players = ['kyuu_v8']
        self.queued_matches = 0

    def log(self, msg: str):
        print('[{}] Server #{}: {}'.format(datetime.now().strftime("%Y-%m-%d %H:%M:%S"), self.id, msg))

    def queue_matches(self):
        self.log("queueing matches..")
        matches = MatchQueue()
        for map_name in self.maps:
            for player in self.test_players:
                for i in range(self.repeat):
                    match = Match(map_name=map_name, player_1=self.main_player, player_2=player, match_id=self.id, seed=MATCH_SEEDS[i])
                    matches.enqueue(match)
                    matches.enqueue(match.reverse_player())
                    self.queued_matches += 2
        self.lock_queue.acquire()
        self.redis.set(SERVER_ID_KEY, self.id)
        self.redis.set(QUEUE_KEY, str(matches))
        self.lock_queue.release()

    def loop(self):
        stop_requested = False
        while True:
            try:
                time.sleep(1)
                self.lock_result.acquire()
                raw_results = self.redis.get(get_result_list_key(self.id))
                if raw_results is None:
                    continue
                results = MatchResultList(repr_str=raw_results.decode())
                pending_matches = self.queued_matches - len(results.list)
                os.system('cls')
                self.log("Match results:")
                print("pending matches: {}".format(pending_matches))
                map_win = Counter()
                map_play = Counter()
                map_win_reason = dict()
                map_lose_reason = dict()
                for test_player in self.test_players:
                    win_count = 0
                    total_matches = 0
                    for result in results.list:
                        if test_player not in [result.match.player_1, result.match.player_2]:
                            continue
                        total_matches += 1
                        if result.winner == self.main_player:
                            win_count += 1

                    if total_matches == 0:
                        continue
                    print("Player: {} | opponent: {} | wins: {} out of {} ({:.2f}%) | version: {} ".format(
                        self.main_player, test_player, win_count, total_matches,
                        win_count/total_matches*100, self.id))
                    print("{:20} | {:4} | {:4} | {:7} | {:10}".format("map", "seed", "side", "result", "reason"))
                    for result in results.list:
                        opponent = result.match.player_1 if result.match.player_1 != self.main_player \
                            else result.match.player_2
                        if opponent != test_player:
                            continue
                        side = 'A' if result.match.player_1 == self.main_player else 'B'
                        is_win = 'win' if result.winner == self.main_player else 'lose'
                        map_play[result.match.map] += 1
                        if result.match.map not in map_win_reason.keys():
                            map_win_reason[result.match.map] = []
                            map_lose_reason[result.match.map] = []
                        if result.winner == self.main_player:
                            map_win[result.match.map] += 1
                            map_win_reason[result.match.map].append(result.win_reason[:3])
                        else:
                            map_lose_reason[result.match.map].append(result.win_reason[:3])

                        print("{:20} | {:4} | {:4} | {:7} | {:3}".format(result.match.map, result.match.seed, side, is_win, result.win_reason[:3]))

                if pending_matches == 0:
                    self.log("finished")
                    print("{:20} | {:10} | {:25} | {:25}".format("map", "win rate %", "win reason", "lose reason"))
                    for map_name in sorted(map_play.keys()):
                        print("{:20} | {:10} | {:25} | {:25}".format(
                            map_name, "{:.2f}%".format((map_win[map_name] / map_play[map_name]) * 100),
                            ','.join(map_win_reason[map_name]), ','.join(map_lose_reason[map_name])))
                    break
                if stop_requested:
                    break
            except KeyboardInterrupt:
                stop_requested = True
            finally:
                if self.lock_result.owned():
                    self.lock_result.release()


if __name__ == '__main__':
    test_players_ = sys.argv[1].split(',')
    map_coverage_ = int(sys.argv[2])
    main_player = 'kyuu'
    repeat = 1
    if len(sys.argv) >= 4:
        repeat = int(sys.argv[3])
    if len(sys.argv) >= 5:
        main_player = sys.argv[4]
    server = TestServer(test_players_, map_coverage_, main_player, repeat)
    server.queue_matches()
    server.loop()
