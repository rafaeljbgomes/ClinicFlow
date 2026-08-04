pipeline {
    agent { label 'clinicflow-ci' }

    parameters {
        choice(name: 'VALIDATION_LEVEL', choices: ['FAST', 'FULL'], description: 'FAST runs deterministic quality gates; FULL also runs integration, mutation, image, and browser validation.')
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
        timeout(time: 90, unit: 'MINUTES')
        timestamps()
    }

    environment {
        CI_RUN_ID = "jenkins-${BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }
        stage('Repository hygiene') {
            steps { sh 'pwsh -NoLogo -NoProfile -File ./scripts/ci/Test-RepositoryHygiene.ps1' }
        }
        stage('Backend fast') {
            steps { sh 'pwsh -NoLogo -NoProfile -File ./scripts/ci/Invoke-BackendFast.ps1' }
        }
        stage('Frontend quality') {
            steps { sh 'pwsh -NoLogo -NoProfile -File ./scripts/ci/Invoke-Frontend.ps1' }
        }
        stage('Platform validation') {
            steps { sh 'pwsh -NoLogo -NoProfile -File ./scripts/ci/Test-Platform.ps1' }
        }
        stage('Backend full') {
            when { expression { params.VALIDATION_LEVEL == 'FULL' } }
            steps { sh 'pwsh -NoLogo -NoProfile -File ./scripts/ci/Invoke-BackendFull.ps1' }
        }
        stage('Container images') {
            when { expression { params.VALIDATION_LEVEL == 'FULL' } }
            steps { sh 'pwsh -NoLogo -NoProfile -File ./scripts/ci/Build-Images.ps1' }
        }
        stage('Compose and Playwright') {
            when { expression { params.VALIDATION_LEVEL == 'FULL' } }
            steps { sh 'pwsh -NoLogo -NoProfile -File ./scripts/ci/Invoke-ComposePlaywright.ps1 -RunId $CI_RUN_ID' }
        }
    }

    post {
        always {
            sh 'pwsh -NoLogo -NoProfile -File ./scripts/ci/Stop-IsolatedCompose.ps1 -RunId $CI_RUN_ID || true'
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml,**/target/failsafe-reports/*.xml,frontend/test-results/**/*.xml'
            archiveArtifacts allowEmptyArchive: true, artifacts: 'reports/**,**/target/site/jacoco/**,**/target/pit-reports/**,frontend/playwright-report/**,frontend/test-results/**', fingerprint: false
            publishHTML(target: [allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'coverage-report/target/site/jacoco-aggregate', reportFiles: 'index.html', reportName: 'JaCoCo aggregate'])
            publishHTML(target: [allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'frontend/playwright-report', reportFiles: 'index.html', reportName: 'Playwright'])
            cleanWs(deleteDirs: true, disableDeferredWipeout: true, notFailBuild: true)
        }
    }
}
