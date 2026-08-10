// Local learning pipeline — mirrors .github/workflows/ci.yml
// GitHub Actions remains the source of truth for PRs / main.

pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
  }

  stages {
    stage('CI') {
      parallel {
        stage('Quality') {
          steps {
            sh '''
              ./mvnw -B -ntp \
                spotless:check \
                checkstyle:check \
                pmd:check \
                pmd:cpd-check \
                -DskipTests \
                compile \
                spotbugs:check
            '''
          }
        }
        stage('Unit tests') {
          steps {
            sh './mvnw -B -ntp test jacoco:report jacoco:check -DskipITs'
          }
        }
        stage('Integration tests') {
          steps {
            sh '''
              ./mvnw -B -ntp verify \
                -DskipUnitTests=true \
                -Djacoco.skip=true \
                -Dspotbugs.skip=true \
                -Dpmd.skip=true \
                -Dcpd.skip=true \
                -Dcheckstyle.skip=true \
                -Dspotless.check.skip=true
            '''
          }
        }
      }
    }

    stage('Package') {
      steps {
        sh '''
          ./mvnw -B -ntp package -DskipTests \
            -Dspotless.check.skip=true \
            -Dcheckstyle.skip=true \
            -Dpmd.skip=true \
            -Dcpd.skip=true \
            -Dspotbugs.skip=true \
            -Djacoco.skip=true
        '''
      }
    }
  }

  post {
    always {
      junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml,**/target/failsafe-reports/*.xml'
      recordCoverage(
        tools: [[parser: 'JACOCO', pattern: '**/target/site/jacoco/jacoco.xml']],
        id: 'jacoco',
        name: 'JaCoCo Coverage',
        sourceCodeRetention: 'LAST_BUILD',
        sourceDirectories: [[path: 'voyage-app/src/main/java']]
      )
      archiveArtifacts artifacts: '**/target/site/jacoco/**,**/target/surefire-reports/**,**/target/failsafe-reports/**', allowEmptyArchive: true
    }
  }
}
