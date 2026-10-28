import urllib.request
import json
import time

def req(url, method='GET', data=None, token=None):
    headers = {'Content-Type': 'application/json', 'Accept': 'application/json'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    bdata = json.dumps(data).encode('utf-8') if data else None
    r = urllib.request.Request(url, data=bdata, headers=headers, method=method)
    with urllib.request.urlopen(r) as res:
        return res.status, json.loads(res.read().decode('utf-8'))

print("=== STEP 1: AUTHENTICATION ===")
s_stat, s_body = req('http://localhost:8080/api/v1/auth/login', 'POST', {'username': 'shipper@soa8.org', 'password': 'ShipperPass123!'})
shipper_token = s_body['token']
print("1. Shipper token acquired successfully.")

p_stat, p_body = req('http://localhost:8080/api/v1/auth/login', 'POST', {'username': 'planner@soa8.org', 'password': 'PlannerPass123!'})
planner_token = p_body['token']
print("2. Planner token acquired successfully.")

print("\n=== STEP 2: DECLARATION CREATION ===")
decl_data = {
    'referenceNumber': f'DEC-PASS-{int(time.time())}',
    'shipperId': 'usr-shipper-001',
    'origin': 'JNPT Mumbai',
    'destination': 'Tughlakabad Delhi',
    'items': [
        {
            'cargoItemId': 'ITEM-SOLVENT-A',
            'description': 'Flammable Solvent Alpha',
            'hazardClass': '3',
            'unNumber': 'UN1993',
            'lengthMm': 1200,
            'widthMm': 1000,
            'heightMm': 1000,
            'weightKg': 500,
            'stackable': True,
            'quantity': 1
        },
        {
            'cargoItemId': 'ITEM-SOLVENT-B',
            'description': 'Flammable Solvent Beta',
            'hazardClass': '3',
            'unNumber': 'UN1993',
            'lengthMm': 1200,
            'widthMm': 1000,
            'heightMm': 1000,
            'weightKg': 400,
            'stackable': True,
            'quantity': 1
        }
    ]
}
_, decl = req('http://localhost:8080/api/v1/declarations', 'POST', decl_data, shipper_token)
decl_id = decl['id']
print(f"Declaration created: ID={decl_id}, Ref={decl['referenceNumber']}")

print("\n=== STEP 3: INITIALIZE ASSESSMENT WITH PINNED RULE SNAPSHOT ===")
compartment = {
    'compartmentId': 'comp-standard-40ft',
    'name': 'Standard 40ft High-Cube Trailer',
    'lengthMm': 12000,
    'widthMm': 2400,
    'heightMm': 2600,
    'maxPayloadKg': 25000
}
asmt_data = {
    'declarationId': decl_id,
    'declarationVersion': 1,
    'ruleSnapshotId': 'snap-2026-q4-v1',
    'compartment': compartment
}
_, asmt = req('http://localhost:8080/api/v1/assessments', 'POST', asmt_data, planner_token)
asmt_id = asmt['id']
print(f"Assessment created: ID={asmt_id}, Workflow={asmt['workflowStatus']}, Snapshot={asmt['ruleSnapshotId']}")

print("\n=== STEP 4: SOLVER 1 - HEURISTIC_EP_3D_V1 ===")
job_req = {
    'declarationId': decl_id,
    'compartment': compartment,
    'items': decl['items'],
    'ruleSnapshotId': 'snap-2026-q4-v1',
    'algorithm': 'HEURISTIC_EP_3D_V1',
    'async': False,
    'timeLimitSeconds': 20
}
_, job = req('http://localhost:8080/api/v1/optimization-jobs', 'POST', job_req, planner_token)
job_id = job['jobId']
print(f"Heuristic Job submitted: ID={job_id}, Status={job['status']}")

_, layout_heuristic = req(f'http://localhost:8080/api/v1/optimization-jobs/{job_id}/result', 'GET', token=planner_token)
print(f"Heuristic Result: Status={layout_heuristic['solverStatus']}, Provenance={layout_heuristic['provenance']}, Placements={len(layout_heuristic['placements'])}")
assert layout_heuristic['provenance'] == 'HEURISTIC_EP_3D_V1'

print("\n=== STEP 5: SOLVER 2 - PYTHON_CPSAT_3D_V1 ===")
job_req_cpsat = {
    'declarationId': decl_id,
    'compartment': compartment,
    'items': decl['items'],
    'ruleSnapshotId': 'snap-2026-q4-v1',
    'algorithm': 'PYTHON_CPSAT_3D_V1',
    'async': True,
    'timeLimitSeconds': 20
}
_, job_cpsat = req('http://localhost:8080/api/v1/optimization-jobs', 'POST', job_req_cpsat, planner_token)
job_id_cpsat = job_cpsat['jobId']
print(f"CP-SAT Job submitted: ID={job_id_cpsat}")

layout_cpsat = None
for i in range(20):
    time.sleep(1)
    _, st = req(f'http://localhost:8080/api/v1/optimization-jobs/{job_id_cpsat}/status', 'GET', token=planner_token)
    if st['status'] == 'COMPLETED':
        _, layout_cpsat = req(f'http://localhost:8080/api/v1/optimization-jobs/{job_id_cpsat}/result', 'GET', token=planner_token)
        break
print(f"CP-SAT Result: Status={layout_cpsat['solverStatus']}, Provenance={layout_cpsat['provenance']}, Placements={len(layout_cpsat['placements'])}")
assert layout_cpsat['provenance'] == 'PYTHON_ORTOOLS_CPSAT_3D_V1'

print("\n=== STEP 6: RECORD CANDIDATE LAYOUT ===")
_, rec_asmt = req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/candidate', 'POST', layout_cpsat, planner_token)
print(f"Candidate attached: Status={rec_asmt['workflowStatus']}, Version={rec_asmt['candidateLayoutVersion']}, Digest={rec_asmt['candidateContentDigest']}")

print("\n=== STEP 7: INDEPENDENT ZERO-TRUST VALIDATION ===")
_, snap = req('http://localhost:8080/api/v1/rule-snapshots/snap-2026-q4-v1', 'GET', token=planner_token)
val_req = {
    'layout': layout_cpsat,
    'compartment': compartment,
    'ruleSnapshot': snap
}
_, val_res = req('http://localhost:8080/api/v1/validations', 'POST', val_req, planner_token)
print(f"Independent Validation Engine Run: Passed={val_res['passed']}, FindingsCount={len(val_res['findings'])}, EvaluatedRules={val_res['evaluatedRulesCount']}")
assert val_res['passed'] is True, "Expected valid candidate to pass validation"

# Bind validation to assessment
_, bound_asmt = req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/validation', 'POST', val_res, planner_token)
print(f"Validation Result Bound to Assessment: Status={bound_asmt['workflowStatus']}")
assert bound_asmt['workflowStatus'] == 'PENDING_APPROVAL'

print("\n=== STEP 8: AUTHORITATIVE HUMAN APPROVAL ===")
appr_req = {
    'layoutVersion': bound_asmt['candidateLayoutVersion'],
    'expectedDigest': layout_cpsat['contentDigest'],
    'plannerId': 'usr-planner-001',
    'notes': 'Verified compliant with CMV/IMDG rules'
}
_, appr_asmt = req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/approve', 'POST', appr_req, planner_token)
print(f"Approved Successfully! Status={appr_asmt['workflowStatus']}")
assert appr_asmt['workflowStatus'] == 'APPROVED'

print("\n=== STEP 9: RE-QUERY PERSISTENCE ===")
_, reloaded = req(f'http://localhost:8080/api/v1/assessments/{asmt_id}', 'GET', token=planner_token)
print(f"Reloaded Assessment ID={reloaded['id']}: Status={reloaded['workflowStatus']}, ApprovedBy={reloaded.get('approvedByPlannerId')}")
assert reloaded['workflowStatus'] == 'APPROVED'
print(">>> APPROVAL WORKFLOW VERIFIED SUCCESSFULLY WITH FULL PERSISTENCE & AUDIT PROVENANCE! <<<")
