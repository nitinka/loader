[ -d log ] || mkdir log
java -cp ./lib/*:./usr/share/loader-server/platformLibs/* com.flipkart.perf.LoaderNode -c ./etc/loader-node/loader-node.yml $* >> log/loader-node.log 2>&1 &
