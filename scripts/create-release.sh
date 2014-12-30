# Setting Loader Server File system
echo "Setting up Server info"
CODE_ROOT=../loader-node
COMPONENT=loader-server
RELEASE_VERSION=loader-$1

mkdir -p ./releases/$RELEASE_VERSION/etc/loader-node
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
for file in `ls -1 $CODE_ROOT/config`
do
	cp -r $CODE_ROOT/config/$file ./releases/$RELEASE_VERSION/etc/loader-node/	
done


#Setting up Core Libraries
echo "\n****Copying loader-core libraries for loader-server"
cp -R ../loader-core/target/platform.zip ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/platformLibs/
unzip ../loader-core/target/platform.zip -d ./releases/$RELEASE_VERSION/usr/share/$COMPONENT/platformLibs/

#Setting up Out of Box Perf Operations
echo "\n****Copying Out out Box Perf Operations"
cp ../loader-http-operations/target/loader-http-operations-*-jar-with-dependencies.jar ./releases/$RELEASE_VERSION/usr/share/loader-server/unDeployedLibs/
cp ../loader-common-operations/target/loader-common-operations-*-jar-with-dependencies.jar ./releases/$RELEASE_VERSION/usr/share/loader-server/unDeployedLibs/

#Setting Sample Runs
echo "\n****Setting up Sample Performance Run Schemas"
cp -r ../loader-node/sample/runs/SampleHttpGetFor10000Times ./releases/$RELEASE_VERSION/var/log/loader-server/runs/
cp -r ../loader-node/sample/runs/SampleHttpGetFor30Seconds ./releases/$RELEASE_VERSION/var/log/loader-server/runs/


# Copying libs required to start loader-node
mkdir -p ./releases/$RELEASE_VERSION/lib
for file in `ls -1 ../loader-node/target/*.jar`
do
        cp  $file ./releases/$RELEASE_VERSION/lib/
done

for file in `ls -1 ../loader-node/target/lib/*.jar`
do
        cp -r $file ./releases/$RELEASE_VERSION/lib/
done

cp ./function-configs/* ./releases/$RELEASE_VERSION/usr/share/loader-server/config/

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

# Replace paths
sed -i '' 's/loader-node/etc\/loader-node/g' ./releases/$RELEASE_VERSION/etc/loader-node/loader-node.yml
sed -i '' 's/config\///g' ./releases/$RELEASE_VERSION/etc/loader-node/loader-node.yml

sed -i '' 's/\/usr/\.\/usr/g' ./releases/$RELEASE_VERSION/etc/loader-node/loader-agent.json
sed -i '' 's/\/var/\.\/var/g' ./releases/$RELEASE_VERSION/etc/loader-node/loader-agent.json
sed -i '' 's/\/etc/\.\/etc/g' ./releases/$RELEASE_VERSION/etc/loader-node/loader-agent.json

sed -i '' 's/\/usr/\.\/usr/g' ./releases/$RELEASE_VERSION/etc/loader-node/loader-server.json
sed -i '' 's/\/var/\.\/var/g' ./releases/$RELEASE_VERSION/etc/loader-node/loader-server.json
sed -i '' 's/\/etc/\.\/etc/g' ./releases/$RELEASE_VERSION/etc/loader-node/loader-server.json
sed -i '' 's/etc\/loader-server/etc\/loader-node/g' ./releases/$RELEASE_VERSION/etc/loader-node/loader-server.json



# Moving Start script
cp start-node.sh ./releases/$RELEASE_VERSION/
cp stop-node.sh ./releases/$RELEASE_VERSION/

echo "Creating loader zip file"
cd ./releases
zip -r $RELEASE_VERSION.zip $RELEASE_VERSION
mv $RELEASE_VERSION.zip ../
cd -
