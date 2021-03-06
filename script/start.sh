#!/bin/bash
sh shutdown.sh

pidKeeper=pidKeeper

date=`date '+%Y-%m-%d_%H_%M_%S'`

if [ ! -d "logs" ]; then
    mkdir logs
fi

if [ -f "server.log" ]; then
    mv server.log logs/server_$date.log
fi

nohup sh zstart.sh  > server.log 2>&1 &


echo $! > "$pidKeeper"

sleep 0.5

tail -f server.log
# sh tail.sh &