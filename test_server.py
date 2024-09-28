import redis

import os
import random
import sys
import time

from datetime import datetime

from redis import lock

from test_utils import *

SERVER_ID_KEY = "server_id"

random.seed(69)


class TestServer:
    def __init__(self, test_players: List[str], map_coverage: int, main_player: str = 'kyuu'):
        self.redis = redis.Redis(host='localhost', port=6379, db=0)
        self.lock_queue = lock.Lock(self.redis, QUEUE_LOCK_KEY)
        self.id = datetime.now().strftime("%Y-%m-%d %H:%M")
        self.lock_result = lock.Lock(self.redis, get_result_lock_key(self.id))
        all_maps = [
            # 'A',
            # 'Basic1',
            # 'Basic2',
            # 'Basic3',
            'Capsules',
            'Chains',
            'Circle',
            'CloseHQs',
            # 'Comb',
            # 'FastPaced',
            # 'Flooded',
            # 'FreeForAll',
            # 'HiddenBase',
            # 'Keys',
            # 'KingOfTheHill',
            # 'Labyrinth',
            # 'LightPolarizer',
            # 'MiniGrid',
            # 'OneTwoTree',
            # 'OneVsOne',
            # 'Riverside',
            # 'SandsOfTime',
            # 'ShortOnReputation',
            # 'Smol',
            # 'Spiral',
            'Squares',
            'SumonersRift',
            'SwimmingPool',
            'TinySplit',
            'TinyTiny',
            'ToughDecision',
            'TunnelVision',
            'Weirdo',
            'WhereYouSpawn',
            'X',
            'xdd',
            'ZigZag',
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
                match = Match(map_name=map_name, player_1=self.main_player, player_2=player, match_id=self.id)
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
                    print("{:20} | {:4} | {:4}".format("map", "side", "result"))
                    for result in results.list:
                        opponent = result.match.player_1 if result.match.player_1 != self.main_player \
                            else result.match.player_2
                        if opponent != test_player:
                            continue
                        side = 'A' if result.match.player_1 == self.main_player else 'B'
                        is_win = 'win' if result.winner == self.main_player else 'lose'
                        print("{:20} | {:4} | {:4}".format(result.match.map, side, is_win))

                if pending_matches == 0:
                    self.log("finished")
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
    main_player = 'kyuu_pathfind'
    if len(sys.argv) >= 4:
        main_player = sys.argv[3]
    server = TestServer(test_players_, map_coverage_, main_player)
    server.queue_matches()
    server.loop()
