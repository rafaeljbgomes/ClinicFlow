FROM ghcr.io/zaproxy/zaproxy:2.17.0@sha256:17711b689795c5519c512bafee363b3976ce8457358db8405c6961e18c72dc66
COPY --chown=zap:zap scripts/ci/dast.py /opt/clinicflow/dast.py
COPY --chown=zap:zap .ci/dast-requests.json /opt/clinicflow/requests.json
# The base image already runs as zap; declaring it keeps the scanner non-root if the base changes.
USER zap
ENTRYPOINT ["python3", "/opt/clinicflow/dast.py"]
