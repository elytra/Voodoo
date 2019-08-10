pipeline {
    agent any
	options {
		buildDiscarder(logRotator(artifactNumToKeepStr: '10'))
	}
	stages {
	    stage("init") {
	        steps {
	            sh 'git submodule update --init --recursive'
	        }
	    }
		stage("test") {
			steps {
                sh './gradlew clean'
				sh './gradlew test -S'
			}
		}
	    stage('publish') {
            steps {
                sh './gradlew publish -S'
            }
        }
		stage('counter') {
			steps {
				sh './gradlew buildnumberIncrement'
			}
		}
	}
	post {
        always {
            sh './gradlew writeMavenUrls'
            script {
               env.URLS = readFile('mavenUrls.txt')
            }
            withCredentials([string(credentialsId: 'discord.webhook.url', variable: 'discordWebhookId')]) {
                discordSend(
                    description: "Downloads: \n${env.URLS}",
                    footer: "build in ${currentBuild.durationString}",
                    link: env.BUILD_URL,
                    result: currentBuild.currentResult,
                    title: JOB_NAME,
                    webhookURL: discordWebhookId
                )
            }
        }
    }
}