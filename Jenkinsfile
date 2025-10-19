// Jenkinsfile for 06-fullstack-elk-app-test: build image with Kaniko and push to internal registry
// Adjust REGISTRY and secret names below as needed for your environment

pipeline {
    agent any

    // Add properties to control build behavior in Multibranch Pipeline
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    // Note: triggers in Multibranch pipelines can cause first-scan hangs.
    // Configure polling at the Multibranch job level instead, or use GitHub webhooks.
    // triggers {
    //     pollSCM('H/5 * * * *')
    // }

    environment {
        REGISTRY = 'docker-repo.homelab.com'
        APP_NAME = 'fullstack-elk-app-test'
        BUILD_TAG = "${env.BUILD_NUMBER}"
        // Default to false since we removed the parameter
        KANIKO_ENABLED = 'false'
    }

    stages {
        // Add a small smoke stage that always runs and prints host info so you'll see logs when you
        // trigger a build manually. This helps diagnose "no logs" situations.
        stage('Smoke test (force visible logs)') {
            agent any
            steps {
                echo 'Smoke test: this build prints basic info to ensure logs are visible.'
                sh 'echo "BUILD_NUMBER=$BUILD_NUMBER"'
                sh 'echo "JOB_NAME=$JOB_NAME"'
                sh 'echo "NODE_NAME=$NODE_NAME"'
                sh 'echo "WORKSPACE=$WORKSPACE"'
                sh 'echo "HOME=$HOME"'
                sh 'echo "PATH=$PATH"'
                // show working directory and a quick listing
                sh 'pwd; ls -la || true'
                // if WORKSPACE exists, list it
                sh 'if [ -n "$WORKSPACE" ] && [ -d "$WORKSPACE" ]; then echo "Workspace listing:"; ls -la "$WORKSPACE" || true; fi'
                // useful tool versions (safe, non-secret)
                sh 'git --version || true'
                sh 'java -version || true'
                sh 'mvn -v || true'
            }
        }

        stage('Validate environment') {
            agent any
            steps {
                echo "Validating Jenkins node and tooling..."
                sh 'echo "Running on: $(hostname)"'
                sh 'git --version || true'
                sh 'docker --version || true'
            }
        }

        stage('Checkout') {
            agent any
            steps {
                echo 'Checking out source code...'
                checkout scm
                sh 'git rev-parse --show-toplevel || true'
            }
        }

        stage('Build Application') {
            // Use Maven directly on the Jenkins agent; if missing, download a portable Maven into the workspace
            agent any
            steps {
                echo 'Building application with Maven...'
                sh '''
                    set -euo pipefail
                    # Choose Maven version to download if not present
                    MAVEN_VERSION=${MAVEN_VERSION:-3.8.9}

                    if command -v mvn >/dev/null 2>&1; then
                        echo "Using system mvn: $(mvn -v | head -n1)"
                        MVN_CMD="mvn"
                    else
                        echo "Maven not found on agent — downloading portable Maven ${MAVEN_VERSION} into workspace"
                        MAVEN_DIR="$WORKSPACE/.maven"
                        mkdir -p "$MAVEN_DIR"
                        TARFILE="$MAVEN_DIR/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
                        if [ ! -f "$TARFILE" ]; then
                            if command -v curl >/dev/null 2>&1; then
                                curl -fsSL "https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -o "$TARFILE"
                            elif command -v wget >/dev/null 2>&1; then
                                wget -q -O "$TARFILE" "https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
                            else
                                echo "ERROR: neither curl nor wget is available to download Maven"
                                exit 1
                            fi
                        else
                            echo "Found cached Maven archive"
                        fi

                        tar -xzf "$TARFILE" -C "$MAVEN_DIR"
                        MAVEN_HOME="$MAVEN_DIR/apache-maven-${MAVEN_VERSION}"
                        export MAVEN_HOME
                        export PATH="$MAVEN_HOME/bin:$PATH"
                        MVN_CMD="$MAVEN_HOME/bin/mvn"
                        echo "Downloaded Maven: $($MVN_CMD -v | head -n1)"
                    fi

                    # Run the maven build
                    $MVN_CMD -B -DskipTests clean package
                '''
            }
        }

        stage('Build Docker Image (Kaniko)') {
            // This stage is optional and only runs when KANIKO_ENABLED is set to 'true'.
            when {
                expression { return env.KANIKO_ENABLED == 'true' }
            }
            // Keep the original Kubernetes pod template here for Kaniko usage. This requires
            // the Kubernetes plugin and appropriate secrets configured in Jenkins.
            agent {
                kubernetes {
                    yaml '''
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins
  containers:
  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug
    command:
    - /busybox/cat
    tty: true
    env:
    - name: DOCKER_CONFIG
      value: /kaniko/.docker
    volumeMounts:
    - name: docker-config
      mountPath: /kaniko/.docker
  - name: maven
    image: maven:3.9-eclipse-temurin-17
    command:
    - cat
    tty: true
    volumeMounts:
    - name: m2-cache
      mountPath: /root/.m2
  volumes:
  - name: docker-config
    secret:
      secretName: docker-registry-credentials
  - name: m2-cache
    emptyDir: {}
'''
                }
            }
            steps {
                container('kaniko') {
                    echo 'Building Docker image with Kaniko...'
                    sh """
/kaniko/executor \
  --context=${WORKSPACE} \
  --dockerfile=Dockerfile \
  --destination=${REGISTRY}/${APP_NAME}:${BUILD_TAG} \
  --destination=${REGISTRY}/${APP_NAME}:latest \
  --cache=true \
  --cache-repo=${REGISTRY}/${APP_NAME}/cache \
  --skip-tls-verify \
  --verbosity=info
"""
                }
            }
        }

        stage('Deploy to Kubernetes (dry-run)') {
            agent any
            steps {
                echo '(Optional) Deploying to Kubernetes (dry-run)...'
                sh """
echo "kubectl set image deployment/${APP_NAME} ${APP_NAME}=${REGISTRY}/${APP_NAME}:${BUILD_TAG} -n production"
echo "kubectl rollout status deployment/${APP_NAME} -n production"
"""
            }
        }
    }

    post {
        success {
            echo "✅ Build ${BUILD_TAG} completed successfully!"
            echo "Image: ${REGISTRY}/${APP_NAME}:${BUILD_TAG}"
        }
        failure {
            echo "❌ Build ${BUILD_TAG} failed!"
        }
    }
}
