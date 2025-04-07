pipeline {
    agent any

    environment {
        SCANNER_HOME = '/opt/sonar-scanner'  // Update with the correct path if needed
        VERSION = "${BUILD_NUMBER}"
        ARTIFACT_NAME = "angular-devops-app-artifact-${VERSION}.tar.gz"
        S3_BUCKET = 's3://ansible20-angular-app'
        SONARQUBE_URL = "http://18.213.2.225:9000/"
        AWS_ACCESS_KEY_ID = credentials('AWS_ACCESS_KEY_ID')
        AWS_SECRET_ACCESS_KEY = credentials('AWS_SECRET_ACCESS_KEY')  // You may need to add this if you're using AWS
        PATH = "${PATH}:${SCANNER_HOME}/bin"  // Add SonarQube Scanner to PATH
    }

    parameters {
        string(name: 'ROLLBACK_VERSION', defaultValue: '', description: 'Version to rollback to')
    }

    stages {
        stage('Checkout Code') {
            steps {
                checkout scm
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withCredentials([string(credentialsId: 'SonarQube_Token', variable: 'SonarQube_Token')]) {
                    withSonarQubeEnv('SonarQube_Token') {  // Ensure SonarQube is configured in Jenkins
                        sh """
                        sonar-scanner \
                        -Dsonar.projectKey=angular-devops-app \
                        -Dsonar.sources=. \
                        -Dsonar.host.url=${SONARQUBE_URL} \
                        -Dsonar.login=sqp_febce8fc5a1a953848d24f0d94279d85cc9ed4cd 
                        """
                    }
                }
            }
        }

        stage('Build & Deploy') {
            steps {
                ansiblePlaybook(
                    playbook: '02-angular-app.yml',
                    inventory: 'hosts.ini',
                    extraVars: [version: VERSION]
                )
            }
        }

        stage('Archive Artifact') {
            steps {
                sh """
                    aws s3 cp ${ARTIFACT_NAME} ${S3_BUCKET}/${ARTIFACT_NAME} --region us-east-1
                """
            }
        }

        stage('Rollback') {
            when {
                expression { return params.ROLLBACK_VERSION != '' }
            }
            steps {
                // Download the artifact from S3
                sh """
                    aws s3 cp ${S3_BUCKET}/angular-app-${params.ROLLBACK_VERSION}.tar.gz . --region us-east-1
                """
                // Deploy the previous artifact version
                ansiblePlaybook(
                    playbook: '02-angular-app.yml',
                    inventory: 'hosts.ini',
                    extraVars: [artifactName: "angular-app-${params.ROLLBACK_VERSION}.tar.gz"]
                )
            }
        }

        stage('Setup MariaDB') {
            steps {
                script {
                    // Optionally, set up MariaDB if needed
                    ansiblePlaybook(
                        playbook: '03-mariadb-and-api.yml',  // If you need a separate MariaDB setup playbook
                        inventory: 'hosts.ini'
                    )
                }
            }
        }
    }

    post {
        success {
            echo "Build, SonarQube analysis, and deploy successful!"
        }
        failure {
            echo "Build, SonarQube analysis, or deploy failed!"
        }
    }
}
