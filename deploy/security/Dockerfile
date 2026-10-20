FROM ghcr.io/zaproxy/zaproxy:2.17.0@sha256:17711b689795c5519c512bafee363b3976ce8457358db8405c6961e18c72dc66
COPY --chown=zap:zap scripts/ci/dast.py /opt/clinicflow/dast.py
COPY --chown=zap:zap .ci/dast-requests.json /opt/clinicflow/requests.json
COPY --chown=zap:zap auth-service/src/main/java/com/clinicflow/auth/infrastructure/bootstrap/DemoDataBootstrap.java /opt/clinicflow/DemoDataBootstrap.java
ENTRYPOINT ["python3", "/opt/clinicflow/dast.py"]
