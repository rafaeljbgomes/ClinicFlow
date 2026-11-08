FROM ghcr.io/zaproxy/zaproxy:2.17.0@sha256:9624669db72f54d86c4b46a7064518bd9bf79d8c61a4c31befa9923489f328af
COPY --chown=zap:zap scripts/ci/dast.py /opt/clinicflow/dast.py
COPY --chown=zap:zap .ci/dast-requests.json /opt/clinicflow/requests.json
# The base image already runs as zap; declaring it keeps the scanner non-root if the base changes.
USER zap
ENTRYPOINT ["python3", "/opt/clinicflow/dast.py"]
