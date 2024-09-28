from typing import List

QUEUE_LOCK_KEY = "lock_match_queue"
QUEUE_KEY = "match_queue"

RESULT_LOCK_KEY = "lock_result_"
RESULT_KEY = "result_"


def get_result_list_key(match_id: str):
    return RESULT_KEY + match_id


def get_result_lock_key(match_id: str):
    return RESULT_LOCK_KEY + match_id


class Match:
    def __init__(self, repr_str: str = None, map_name: str = None, player_1: str = None, player_2: str = None,
                 match_id: str = None):
        if repr_str is None:
            self.map: str = map_name
            self.player_1: str = player_1
            self.player_2: str = player_2
            self.id: str = match_id
            self.repr: str = '{},{},{},{}'.format(map_name, player_1, player_2, match_id)
        else:
            if repr_str.count(',') != 3:
                raise ValueError("Expected repr format: map,player_1,player_2,id")
            self.repr = repr_str
            self.map, self.player_1, self.player_2, self.id = self.repr.split(',')

    def reverse_player(self):
        return Match(map_name=self.map, player_1=self.player_2, player_2=self.player_1, match_id=self.id)

    def __repr__(self):
        return '{} on {} - {} vs {}'.format(self.id, self.map, self.player_1, self.player_2)

    def __str__(self):
        return self.__repr__()


class MatchResult:
    def __init__(self, repr_str: str = None, match: Match = None, winner: str = None, winner_side: str = None,
                 round_count: int = None, win_reason: str = None):
        if repr_str is None:
            self.match = match
            self.winner = winner
            self.winner_side = winner_side
            self.round_count = round_count
            self.win_reason = win_reason
            self.repr: str = '{},{},{},{},{},{},{},{}'.format(
                match.map, match.player_1, match.player_2, match.id, winner, winner_side, round_count, win_reason)
        else:
            if repr_str.count(',') != 7:
                raise ValueError("Expected repr format: map,player_1,player_2,id,winner,winner_side,round_count,"
                                 "win_reason")
            self.repr = repr_str
            self.match = Match(repr_str=','.join(self.repr.split(',')[:4]))
            self.winner, self.winner_side, self.round_count, self.win_reason = self.repr.split(',')[4:]

    def __repr__(self):
        return '{} | winner: {} ({}), round: {}, win reason: {}'.format(
            str(self.match), self.winner, self.winner_side, self.round_count, self.win_reason)

    def __str__(self):
        return self.__repr__()


class MatchQueue:
    def __init__(self, repr_str: str = None, q: List[Match] = tuple()):
        self.q: List[Match] = list(q)
        if repr_str is not None and len(repr_str) > 3:
            matches_str = repr_str.split(';')
            for match_str in matches_str:
                self.q.append(Match(repr_str=match_str))

    def enqueue(self, match: Match):
        self.q.append(match)

    def dequeue(self) -> Match:
        if len(self.q) > 0:
            return self.q.pop(0)
        raise LookupError("Empty queue")

    def __repr__(self):
        return ';'.join(list(map(lambda m: m.repr, self.q)))

    def __str__(self):
        return self.__repr__()


class MatchResultList:
    def __init__(self, repr_str=None, results: List[MatchResult] = tuple()):
        self.list: List[MatchResult] = list(results)
        if repr_str is not None and len(repr_str) > 7:
            results_str = repr_str.split(';')
            for result_str in results_str:
                self.list.append(MatchResult(repr_str=result_str))

    def append(self, result: MatchResult):
        self.list.append(result)
        self.list = sorted(self.list, key=lambda m: m.match.map)

    def __repr__(self):
        return ';'.join(list(map(lambda m: m.repr, self.list)))

    def __str__(self):
        return self.__repr__()
