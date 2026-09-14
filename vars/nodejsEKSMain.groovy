def call (Map configMap){
    pipeline {
      agent {
        node {
            label 'ROBOSHOP'
        }
      }
      environment {
        def appVersion = ""
        acc_id = "970361933543"
        project = configMap.get("project")
        component = configMap.get("component")
        org = "imran806721"
       }

       options {
        disableConcurrentBuilds()
        timeout(time: 15, unit: 'MINUTES')
      }

      stages {
        stage('Read version') {
            steps {
                script {
                    def packageJson = readJSON file: 'package.json'
                    appVersion = packageJson.version

                    echo "The application version is: ${appVersion}"
                }
            }
        }

        stage('Install Dependencies') {
            steps {
                script {
                    sh """
                        npm install
                    """
                }
            }
        }
        
        stage('Dev Deploy') {
            steps {
                script {
                    try {
                        withAWS(credentials: 'aws-cred', region: 'us-east-1') {
                    sh """
                            aws eks update-kubeconfig \
                              --name roboshop \
                              --region us-east-1

                            cd helm

                            helm upgrade --install ${component} . \
                              -f values-dev.yaml \
                              -n roboshop-dev \
                              --create-namespace \
                              --set deployment.imageVersion=${appVersion} \
                              --wait \
                              --timeout 5m

                            kubectl rollout status deployment/${component} \
                              -n roboshop-dev \
                              --timeout=120s
                    """
                        }

                        utils.updateCommitStatus(
                          'success',
                          'Deployed to roboshop-dev',
                          'dev-deploy'
                        )   

                    } catch (Exception e) {

                        utils.updateCommitStatus(
                          'failure',
                          'Deploy to roboshop-dev failed',
                          'dev-deploy'
                        )

                throw e
                    }
               }
           }
    }
        stage('api-tests') {
                steps {
                    script {
                        try {
                            build job: 'ROBOSHOP/catalogue-api-tests', parameters: [
                                string(name: 'NAMESPACE', value: 'roboshop-dev'),
                                string(name: 'COMMIT_ID', value: env.GIT_COMMIT)
                            ], wait: true, propagate: true
                            utils.updateCommitStatus('success', 'catalogue-api-tests passed', 'api-tests')
                        }
                        catch (Exception e) {
                            utils.updateCommitStatus('failure', 'catalogue-api-tests failed', 'api-tests')
                            throw e
                        }
                    }
                }
        }

        stage('push-image-to-ecr'){
                steps{
                    script{
                        try {
                            withAWS(credentials: 'aws-cred', region: 'us-east-1') {
                                sh """
                                docker push ${acc_id}.dkr.ecr.us-east-1.amazonaws.com/${project}/${component}:${appVersion}
                                """
                            }
                            utils.updateCommitStatus('success', 'push image to ECR', 'push-image')
                        }
                        catch(Exception e){
                            utils.updateCommitStatus('failure', 'push image to ECR', 'push-image')
                            throw e
                        }
                    }
                }
            }

            stage('promote-image') {
                when {
                    expression { env_ENVIRONMENT == 'dev' }
                }
                steps {
                    script {
                        try {
                            withAWS(credentials: 'aws-cred', region: 'us-east-1') {
                                sh """
                                    aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${acc_id}.dkr.ecr.us-east-1.amazonaws.com

                                    docker pull ${acc_id}.dkr.ecr.us-east-1.amazonaws.com/${project}/${component}:${appVersion}
                                    docker tag ${acc_id}.dkr.ecr.us-east-1.amazonaws.com/${project}/${component}:${appVersion} ${acc_id}.dkr.ecr.us-east-1.amazonaws.com/${project}/${component}:${env.GIT_COMMIT}
                                    docker push ${acc_id}.dkr.ecr.us-east-1.amazonaws.com/${project}/${component}:${env.GIT_COMMIT}
                                """
                            }
                        }
                    }
                }
            }
        }

      post {
        always {
            echo 'I will always say Hello again!'
        }

        success {
            echo 'I will run when success'
        }

        failure {
            echo 'I will Run when it is failed'
        }
      }
    }
}