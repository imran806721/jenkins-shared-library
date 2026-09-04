def call (){
    pipeline {
        agent any
        
        stages {
            stage('Build') {
                steps {
                    script{
                        sh """
                            echo 'Building..'
                        """
                    }
                    
                }
            }
            stage('Test') {
                steps {
                    echo 'Testing..'
                }
            }
            stage('Deploy') {
                steps {
                    echo 'Deploying....'
                }
            }
        }
    }
}