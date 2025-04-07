pipeline {
    agent any

    environment {
        VERSION = "${BUILD_NUMBER}"
        ARTIFACT_NAME = "angular-devops-app-artifact-${VERSION}.tar.gz"
        S3_BUCKET = 's3://ansible20-angular-app'
        AWS_ACCESS_KEY_ID = credentials('AWS_ACCESS_KEY_ID')
        AWS_SECRET_ACCESS_KEY = credentials('AWS_SECRET_ACCESS_KEY')  // AWS credentials
        EC2_INSTANCE_CREDENTIALS = credentials('ec2-instance')  // The SSH private key
        AWS_DEFAULT_REGION = 'us-east-1'  // Corrected syntax
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
                    aws s3 cp /tmp/angular-devops-app-artifact-${VERSION}.tar.gz ${S3_BUCKET}/angular-devops-app-artifact-${VERSION}.tar.gz --region us-east-1
                """
            }
        }

        stage('Rollback') {
    when {
        expression { return params.ROLLBACK_VERSION != '' }
    }
    steps {
        // Ensure the directory exists and has proper permissions for the extracted files
        sh """
            sudo mkdir -p /home/ubuntu/angular-devops-app
        """
        
        // Extract the artifact from /tmp (where it's already downloaded)
        sh """
            sudo tar -xzvf /tmp/angular-devops-app-artifact-${params.ROLLBACK_VERSION}.tar.gz -C /tmp/
        """

        // Move the extracted artifact from /tmp to the target directory
        sh """
            sudo mv /tmp/angular-devops-app/* /home/ubuntu/angular-devops-app/
        """

        // Use Ansible playbook to set the correct permissions for the artifact
        withCredentials([sshUserPrivateKey(credentialsId: 'ec2-instance', keyFileVariable: 'SSH_KEY')]) {
            ansiblePlaybook(
                playbook: '02-angular-app.yml',
                inventory: 'hosts.ini',
                extraVars: [
                    version: "${VERSION}",
                    artifactName: "angular-devops-app-artifact-${params.ROLLBACK_VERSION}.tar.gz",
                    ansible_ssh_private_key_file: "$SSH_KEY",
                    remoteDir: "/home/ubuntu/angular-devops-app"
                ]
            )
        }

        // Ensure proper ownership and permissions after moving the artifact (if needed)
        sh """
            sudo chown -R ubuntu:ubuntu /home/ubuntu/angular-devops-app/
        """
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
