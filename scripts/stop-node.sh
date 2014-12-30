ps -ef | egrep -i "loader-node" | grep -v grep | awk '{print $2}' | xargs kill -9
