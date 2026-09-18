#!/usr/bin/env python3
"""테스트 서버 전용: 실제 모델을 호출하지 않는 재판 API 통합 검증.

사용 전 backend는 APP_AI_PROVIDER=langchain, AI 주소는 테스트 fixture로 지정한다.
공유/운영 DB에서 실행하지 않는다. 생성한 테스트 사건을 자동 삭제하지 않는다.
"""
import argparse
import json
import time
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:18080/api/v1')
    parser.add_argument('--timeout', type=int, default=120)
    parser.add_argument('--expect-failure', action='store_true')
    parser.add_argument('--fail-verdict', action='store_true')
    parser.add_argument('--delay-seconds', type=int, default=0)
    parser.add_argument('--fixture-url', default='http://127.0.0.1:18001')
    args = parser.parse_args()
    identity = str(uuid.uuid4())

    def request(method, path, data=None, expected=200):
        raw = None if data is None else json.dumps(data).encode()
        req = Request(args.base_url + path, data=raw, method=method, headers={
            'Content-Type': 'application/json', 'X-Demo-User-Id': identity,
        })
        try:
            with urlopen(req, timeout=90) as response:
                status, payload = response.status, json.load(response)
        except HTTPError as error:
            status, payload = error.code, json.load(error)
        assert status == expected, (method, path, status, payload)
        return payload.get('data', payload)

    post = request('POST', '/posts', {
        'title': '자동 검증: 약속 변경과 사전 연락',
        'content': '서로 다른 사정으로 약속이 변경되어 사전 연락 방식에 관해 다툰 가상 사례입니다.',
        'relationshipType': 'COUPLE', 'trialRequested': True,
    }, 201)
    trial = request('POST', f"/posts/{post['postId']}/trials", {
        'visibility': 'PUBLIC', 'aDisplayName': '검증 A', 'bDisplayName': '검증 B',
    }, 201)
    trial_id = trial['trialId']
    path = f'/trials/{trial_id}'
    for side in ('A', 'B'):
        request('PUT', f'{path}/parties/{side}/statement', {
            'incidentTime': '가상 사례의 토요일',
            'situation': f'{side}측은 사전 연락이 충분하지 않았다고 설명합니다.',
            'counterpartAction': '약속 시간을 변경했습니다.',
            'ownAction': '변경 이유를 물었습니다.',
            'afterConversation': '다음 약속에서는 미리 연락하기로 논의했습니다.',
            'desiredResolution': '서로 연락할 수 있는 기준을 합의하고 싶습니다.',
        })
        draft = request('POST', f'{path}/parties/{side}/argument-draft', {})
        assert draft['factSummary'] and draft['argumentText']
        request('POST', f'{path}/parties/{side}/confirm', {})
        if side == 'A':
            edited = {'factSummary': draft['factSummary'],
                      'argumentText': draft['argumentText'] + ' 수정한 내용을 확인했습니다.'}
            request('PUT', f'{path}/parties/{side}/argument-draft', edited)
            assert not request('GET', path)['aParty']['ready'], '수정 후 확정 상태가 남음'
            request('POST', f'{path}/parties/{side}/confirm', {})
    if args.expect_failure:
        req = Request(args.fixture_url + '/__test__/fail-next',
                      data=b'{"count": 1}', method='POST',
                      headers={'Content-Type': 'application/json'})
        with urlopen(req) as response:
            assert response.status == 200
    if args.delay_seconds:
        req = Request(args.fixture_url + '/__test__/delay-next',
                      data=json.dumps({'seconds': args.delay_seconds}).encode(), method='POST',
                      headers={'Content-Type': 'application/json'})
        with urlopen(req) as response:
            assert response.status == 200
    request('POST', f'{path}/start', {})
    observed = set()
    generation_observed = False
    failed = False
    retried = False
    voted = False
    deadline = time.monotonic() + args.timeout
    while time.monotonic() < deadline:
        snapshot = request('GET', f'{path}/snapshot')
        state = snapshot['status']
        observed.add((state, snapshot.get('generationStatus')))
        events = request('GET', f'{path}/events')
        debate = [e for e in events if e['eventType'] in ('A_DEBATE', 'B_DEBATE')]
        sequences = [e['sequence'] for e in events]
        assert len(sequences) == len(set(sequences)), '중복 이벤트 순번'
        if snapshot.get('generationStatus') == 'GENERATING':
            generation_observed = True
            if snapshot.get('generationStage') == 'DEBATE':
                assert state == 'DEBATE'
                assert not snapshot.get('voteOpen'), '생성 중 투표 열림'
        if state in ('VOTING', 'VERDICT', 'ENDED'):
            assert len(debate) == 4, ('공방 미완료 상태로 전이', snapshot, len(debate))
        if snapshot.get('generationStatus') == 'FAILED' and not retried:
            failed = True
            before = len(debate)
            time.sleep(2)
            still = request('GET', f'{path}/snapshot')
            assert still['generationStatus'] == 'FAILED', '실패 상태가 자동으로 넘어감'
            assert still['status'] == state
            after = request('GET', f'{path}/events')
            assert len([e for e in after if e['eventType'] in ('A_DEBATE', 'B_DEBATE')]) == before
            request('POST', f'{path}/ai/retry', {})
            retried = True
        if state == 'VOTING' and not voted:
            if args.fail_verdict:
                req = Request(args.fixture_url + '/__test__/fail-next',
                              data=b'{"count": 1}', method='POST',
                              headers={'Content-Type': 'application/json'})
                with urlopen(req) as response:
                    assert response.status == 200
            request('POST', f'{path}/votes', {'selectedSide': 'A'}, 201)
            voted = True
        if state == 'ENDED':
            result = request('GET', f'{path}/results')
            verdict = result['verdict']
            assert verdict['aFaultRatio'] + verdict['bFaultRatio'] == 100
            assert len(verdict['grounds']) >= 3
            assert result['publicVote']['totalVotes'] == 1
            assert not args.delay_seconds or generation_observed, '생성 대기 관측 실패'
            assert not (args.expect_failure or args.fail_verdict) or failed, '실패/재시도 경로 미검증'
            with urlopen(args.fixture_url + '/__test__/calls', timeout=5) as response:
                calls = [call for call in json.load(response) if call['facts']['trialId'] == trial_id]
            debates = [call['facts'] for call in calls if call['schema'] == 'DebateGeneration']
            assert [call['turn'] for call in debates] == [1, 2, 3, 4], '공방 중복 또는 누락'
            assert [call['side'] for call in debates] == ['A', 'B', 'B', 'A']
            assert [len(call['previousTurns']) for call in debates] == [0, 1, 2, 3]
            judgments = [call['facts'] for call in calls if call['schema'] == 'VerdictGeneration']
            assert len(judgments) == 1 and len(judgments[0]['debateTurns']) == 4
            assert set(judgments[0]['statements']) == {'A', 'B'}
            assert 'publicVote' not in judgments[0]
            print(json.dumps({'trialId': trial_id, 'debateTurns': len(debate),
                              'faultRatios': [verdict['aFaultRatio'], verdict['bFaultRatio']],
                              'failureRecovered': retried, 'generationObserved': generation_observed,
                              'observedStates': sorted(map(str, observed)),
                              'result': 'PASS (test fixture, no paid model call)'}, ensure_ascii=False))
            return
        time.sleep(0.25)
    raise AssertionError(f'시간 초과: trialId={trial_id}, states={observed}')


if __name__ == '__main__':
    main()
