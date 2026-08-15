FROM jenkins/jenkins:2.568.2-jdk21@sha256:8547df3b0db2803d158ecc9499207a056bb30c23fddc18bb5b4a4dc14e77dd09

USER root
RUN git config --system --add safe.directory /workspace/clinicflow \
    && git config --system --add safe.directory /workspace/clinicflow/.git
USER jenkins

COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --latest false --plugin-file /usr/share/jenkins/ref/plugins.txt
