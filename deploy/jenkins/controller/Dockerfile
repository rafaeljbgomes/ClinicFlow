FROM jenkins/jenkins:2.588-jdk21@sha256:4c03015d601fe7a761220979d2dd7bf465091e2d13de775eb1980627811b3aaa

USER root
RUN git config --system --add safe.directory /workspace/clinicflow \
    && git config --system --add safe.directory /workspace/clinicflow/.git
USER jenkins

COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --latest false --plugin-file /usr/share/jenkins/ref/plugins.txt
