// Jenkinsfile for 06-fullstack-elk-app-test: build image with Kaniko and push to internal registry
// Adjust REGISTRY and secret names below as needed for your environment

pipeline {
    agent any

    // Note: triggers in Multibranch pipelines can cause first-scan hangs.
    // Configure polling at the Multibranch job level instead, or use GitHub webhooks.
    // triggers {
    //     pollSCM('H/5 * * * *')
    // }

    parameters {
        booleanParam(name: 'KANIKO_ENABLED', defaultValue: false, description: 'Enable Kaniko build')
    }

    environment {
        REGISTRY = 'decker-repo.homelab.com'
        APP_NAME = 'fullstack-elk-app-test'
        BUILD_TAG = "${env.BUILD_NUMBER}"
        KANIKO_ENABLED = "${params.KANIKO_ENABLED ?: 'false'}"
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
            // Use Dockerized Maven for reproducible builds on most Jenkins agents that support Docker.
            // If your Jenkins can't run docker, change agent to 'any' and use a preinstalled Maven.
            agent {
                docker {
                    image 'maven:3.9-eclipse-temurin-17'
                    args '-v $HOME/.m2:/root/.m2'
                }
            }
            steps {
                echo 'Building application with Maven...'
                // Default: run a real maven build. Adjust -DskipTests as desired.
                sh 'mvn -B -DskipTests package'
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
