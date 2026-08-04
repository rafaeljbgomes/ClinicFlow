FROM jenkins/ssh-agent:7.13.0-jdk21

USER root

ARG NODE_VERSION=24.16.0
ARG POWERSHELL_VERSION=7.5.4
ARG DOCKER_VERSION=29.6.2
ARG COMPOSE_VERSION=2.39.4
ARG HELM_VERSION=3.19.0
ARG KUBECONFORM_VERSION=0.7.0
ARG ACTIONLINT_VERSION=1.7.12
ARG GITLEAKS_VERSION=8.28.0
ARG PLAYWRIGHT_VERSION=1.60.0

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates curl git git-lfs gnupg jq openssl procps tar unzip xz-utils \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSLO "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
    && tar -xJf "node-v${NODE_VERSION}-linux-x64.tar.xz" -C /usr/local --strip-components=1 \
    && rm "node-v${NODE_VERSION}-linux-x64.tar.xz" \
    && node --version \
    && npm --version

RUN mkdir -p /opt/microsoft/powershell/7 \
    && curl -fsSL "https://github.com/PowerShell/PowerShell/releases/download/v${POWERSHELL_VERSION}/powershell-${POWERSHELL_VERSION}-linux-x64.tar.gz" \
       | tar -xz -C /opt/microsoft/powershell/7 \
    && chmod +x /opt/microsoft/powershell/7/pwsh \
    && ln -s /opt/microsoft/powershell/7/pwsh /usr/local/bin/pwsh

RUN curl -fsSL "https://download.docker.com/linux/static/stable/x86_64/docker-${DOCKER_VERSION}.tgz" \
       | tar -xz --strip-components=1 -C /usr/local/bin docker/docker \
    && mkdir -p /usr/local/lib/docker/cli-plugins \
    && curl -fsSL "https://github.com/docker/compose/releases/download/v${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
       -o /usr/local/lib/docker/cli-plugins/docker-compose \
    && chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

RUN curl -fsSL "https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz" \
       | tar -xz --strip-components=1 -C /usr/local/bin linux-amd64/helm \
    && curl -fsSL "https://github.com/yannh/kubeconform/releases/download/v${KUBECONFORM_VERSION}/kubeconform-linux-amd64.tar.gz" \
       | tar -xz -C /usr/local/bin kubeconform \
    && curl -fsSL "https://github.com/rhysd/actionlint/releases/download/v${ACTIONLINT_VERSION}/actionlint_${ACTIONLINT_VERSION}_linux_amd64.tar.gz" \
       | tar -xz -C /usr/local/bin actionlint \
    && curl -fsSL "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_x64.tar.gz" \
       | tar -xz -C /usr/local/bin gitleaks

RUN npx --yes "playwright@${PLAYWRIGHT_VERSION}" install --with-deps chromium \
    && chmod -R a+rX /ms-playwright \
    && npm cache clean --force

RUN apt-get update \
    && apt-get install --no-install-recommends -y libicu76 \
    && rm -rf /var/lib/apt/lists/*

COPY clinicflow-entrypoint.sh /usr/local/bin/clinicflow-entrypoint
RUN chmod +x /usr/local/bin/clinicflow-entrypoint \
    && mkdir -p /home/jenkins/agent /home/jenkins/.m2 /home/jenkins/.npm \
    && git config --system --add safe.directory /workspace/clinicflow \
    && git config --system --add safe.directory /workspace/clinicflow/.git \
    && chown -R jenkins:jenkins /home/jenkins /ms-playwright

ENTRYPOINT ["clinicflow-entrypoint"]
