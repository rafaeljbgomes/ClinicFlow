FROM jenkins/jenkins:2.590-jdk21@sha256:1b8068fd8b82518be3bb2e89560efa44f67f5fbc1ab3a9b59826c7f8f70f2260

USER root
RUN git config --system --add safe.directory /workspace/clinicflow \
    && git config --system --add safe.directory /workspace/clinicflow/.git
USER jenkins

COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --latest false --plugin-file /usr/share/jenkins/ref/plugins.txt
