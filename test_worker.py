import os
import subprocess
import time

import redis

from datetime import datetime

from redis import lock

from test_utils import *


class TestWorker:
    def __init__(self):
        self.redis = redis.Redis(host='localhost', port=6379, db=0)
        self.lock_queue = lock.Lock(self.redis, QUEUE_LOCK_KEY)
        self.id = os.getpid()

    def log(self, msg: str):
        print('[{}] Worker #{}: {}'.format(datetime.now().strftime("%Y-%m-%d %H:%M:%S"), self.id, msg))

    def run_match(self, match: Match):
        replay_file = str(match)
        replay_file = replay_file.replace(' ', '-').replace(':', '_')
        self.log("replay file: " + replay_file)
        replay_folder = 'games\\' + str(match.id).replace(' ', '-').replace(':', '_')
        result = subprocess.run([
            'cmd', '/c', 'ant',
            '-Dmap=' + match.map,
            '-Dpackage1=' + match.player_1,
            '-Dpackage2=' + match.player_2,
            '-Dreplay=' + replay_file,
            '-Dseed=' + str(match.seed),
            'run_replay'], stdout=subprocess.PIPE)
        output = result.stdout.decode('utf-8').split('\n')
        # for o in output:
        #     print(o)
        winner = None
        side = None
        reason = None
        reason_find_str = 'WinCondition: '
        for i in range(len(output)):
            if 'Winner' in output[i]:
                print(output[i].split())
                _, _, winner, _, side = output[i].split()
                side = side.replace('(', '').replace(')', '')
            if reason_find_str in output[i]:
                _, reason = output[i].split(reason_find_str)
        result = MatchResult(match=match, winner=winner, winner_side=side, round_count=7, win_reason=reason)
        self.log(str(result))
        result_lock = lock.Lock(self.redis, get_result_lock_key(match.id))
        try:
            result_lock.acquire()
            result_list_key = get_result_list_key(match.id)
            raw_result_list = self.redis.get(result_list_key)
            result_list = MatchResultList(repr_str=raw_result_list.decode()) if raw_result_list is not None \
                else MatchResultList()
            result_list.append(result)
            subprocess.run([
                'cmd', '/c', 'if not exist ' + replay_folder + ' mkdir ' + replay_folder], stdout=subprocess.PIPE)
            subprocess.run([
                'cmd', '/c', 'move', 'games\\' + replay_file + '.txt', replay_folder], stdout=subprocess.PIPE)
            self.redis.set(result_list_key, str(result_list))
        finally:
            if result_lock.owned():
                result_lock.release()

    def loop(self):
        self.log("started")
        while True:
            try:
                self.lock_queue.acquire()
                matches_str = self.redis.get(QUEUE_KEY).decode()
                match: Match | None = None
                if len(matches_str) > 0:
                    matches = MatchQueue(repr_str=matches_str)
                    match = matches.dequeue()
                    self.log("running match: {}".format(match))
                    self.redis.set(QUEUE_KEY, str(matches))
                self.lock_queue.release()
                if match is not None:
                    self.run_match(match)
                time.sleep(1)
            except KeyboardInterrupt:
                self.log("stopped")
                break
            finally:
                if self.lock_queue.owned():
                    self.lock_queue.release()


if __name__ == '__main__':
    TestWorker().loop()
