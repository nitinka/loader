# Setting Loader Server File system
echo "Setting up Server info"
CODE_ROOT=../loader-node
COMPONENT=loader-server
RELEASE_VERSION=1.0.0

mkdir -p ./releases/$RELEASE_VERSION/etc/$COMPONENT
mkdir -p ./releases/$RELEASE_VERSION/var/log/$COMPONENT
mkdir -p ./releases/$RELEASE_VERSION/var/log/$COMPONENT/jobs
mkdir -p ./releases/$RELEASE_VERSION/var/log/$COMPONENT/runs
mkdir -p ./releases/$RELEASE_VERSION/var/log/$COMPONENT/businessUnits
mkdir -p ./releases/$RELEASE_VERSION/var/log/$COMPONENT/businessUnits
mkdir -p ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/config
mkdir -p ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/platformLibs
mkdir -p ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/agents
mkdir -p ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/libs
mkdir -p ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/unDeployedLibs
touch ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/config/classLibMapping.properties

# Creating Files which need to contain list of values
filesToCreate=("./releases/$RELEASE_VERSION/var/log/loader-server/jobs/runningJobs" "./releases/$RELEASE_VERSION/var/log/loader-server/jobs/queuedJobs" "./releases/$RELEASE_VERSION/var/log/loader-server/jobs/doneFixers.json")
for fileToCreate in "${filesToCreate[@]}"
do
    if [ -f "$fileToCreate" ]
    then
        echo "$fileToCreate exists and not creating again"
    else
        echo "[]" > $fileToCreate
    fi
done

runFile="./releases/$RELEASE_VERSION/var/log/$COMPONENT/businessUnits/default"
if [ -f "$runFile" ]
then
    echo "$runFile exists and not creating again"
else
    echo "{\"name\" : \"default\",\"teams\" : {\"default\" : {\"name\" : \"default\",\"runs\" : []}}}" > $runFile
fi

runFile="./releases/$RELEASE_VERSION/var/log/$COMPONENT/businessUnits/sample"
if [ -f "$runFile" ]
then
    echo "$runFile exists and not creating again"
else
    echo "{\"name\" : \"sample\",\"teams\" : {\"sample\" : {\"name\" : \"sample\",\"runs\" : []}}}" > $runFile
fi

#=====================================================================

#Copy Required Files
echo "\n****Copying Config Files"
cp -r $CODE_ROOT/config/* ./releases/$RELEASE_VERSION/etc/$COMPONENT/

#Setting up Core Libraries
echo "\n****Copying loader-core libraries for loader-server"
cp -R ../loader-core/target/platform.zip ./releases/$RELEASE_VERSION/usr/share/$PKG/platformLibs/
unzip ../loader-core/target/platform.zip -d ./releases/$RELEASE_VERSION/usr/share/$PKG/platformLibs/

#Setting up Out of Box Perf Operations
echo "\n****Copying Out out Box Perf Operations"
cp ../loader-http-operations/target/loader-http-operations-*-jar-with-dependencies.jar ./releases/$RELEASE_VERSION/usr/share/loader-server/unDeployedLibs/
cp ../loader-common-operations/target/loader-common-operations-*-jar-with-dependencies.jar ./releases/$RELEASE_VERSION/usr/share/loader-server/unDeployedLibs/

#Setting Sample Runs
echo "\n****Setting up Sample Performance Run Schemas"
cp -r ../loader-server/sample/runs/SampleHttpGetFor10000Times ./releases/$RELEASE_VERSION/var/log/loader-server/runs/
cp -r ../loader-server/sample/runs/SampleHttpGetFor30Seconds ./releases/$RELEASE_VERSION/var/log/loader-server/runs/

# Setting up loader agent
echo "\n===============Setting up Loader Agent file system================\n"
echo "****Creating Files and Folder Required for loader-agent"
COMPONENT=loader-agent

mkdir -p ./releases/$RELEASE_VERSION/var/log/loader
mkdir -p ./releases/$RELEASE_VERSION/var/log/$COMPONENT/jobs
mkdir -p ./releases/$RELEASE_VERSION/usr/share/$COMPONENT
mkdir -p ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/config
mkdir -p ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/app
mkdir -p ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/lib
mkdir -p ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/libs
mkdir -p ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/platformLibs
touch ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/config/mapping.properties

runningJobsFile=./releases/$RELEASE_VERSION/var/log/loader-agent/jobs/runningJobs

if [ -f "$runningJobsFile" ]
then
        echo "$runningJobsFile exists and not creating again"
else
        echo "[]" > $runningJobsFile 
fi
