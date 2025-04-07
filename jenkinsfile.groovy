pipeline {
    agent any

    environment {
        VERSION = "${BUILD_NUMBER}"
        ARTIFACT_NAME = "angular-devops-app-artifact-${VERSION}.tar.gz"
        S3_BUCKET = 's3://ansible20-angular-app'
        AWS_ACCESS_KEY_ID = credentials('AWS_ACCESS_KEY_ID')
        AWS_SECRET_ACCESS_KEY = credentials('AWS_SECRET_ACCESS_KEY')  // AWS credentials
        EC2_INSTANCE_CREDENTIALS = credentials('ec2-instance')  // The SSH private key
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

        stage('Build & Deploy') {
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'ec2-instance', keyFileVariable: 'SSH_KEY')]) {
                    ansiblePlaybook(
                        playbook: '02-angular-app.yml',
                        inventory: 'hosts.ini',
                        extraVars: [
                            version: "${VERSION}",  // Pass version as extra variable
                            ansible_ssh_private_key_file: "$SSH_KEY"  // Pass SSH key to Ansible
                        ]
                    )
                }
            }
        }

        stage('Archive Artifact') {
            steps {
                sh """
                    aws s3 cp /home/ubuntu/angular-devops-app/${ARTIFACT_NAME} ${S3_BUCKET}/${ARTIFACT_NAME} --region us-east-1
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
                withCredentials([sshUserPrivateKey(credentialsId: 'ec2-instance', keyFileVariable: 'SSH_KEY')]) {
                    ansiblePlaybook(
                        playbook: '02-angular-app.yml',
                        inventory: 'hosts.ini',
                        extraVars: [
                            version: "${VERSION}",  // Ensure version is passed correctly
                            artifactName: "angular-app-${params.ROLLBACK_VERSION}.tar.gz",  // Pass artifact name
                            ansible_ssh_private_key_file: "$SSH_KEY"  // Pass SSH key for rollback
                        ]
                    )
                }
            }
        }

        stage('Setup MariaDB') {
            steps {
                script {
                    // Optionally, set up MariaDB if needed
                    withCredentials([sshUserPrivateKey(credentialsId: 'ec2-instance', keyFileVariable: 'SSH_KEY')]) {
                        ansiblePlaybook(
                            playbook: '03-mariadb-and-api.yml',
                            inventory: 'hosts.ini',
                            extraVars: [
                                version: "${VERSION}",  // Pass version for MariaDB setup
                                ansible_ssh_private_key_file: "$SSH_KEY"  // Pass SSH key
                            ]
                        )
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Build and deploy successful!"
        }
        failure {
            echo "Build and deploy failed!"
        }
    }
}
