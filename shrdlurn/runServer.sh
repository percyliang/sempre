#!/bin/sh
#screen -S sempre-server -X quit
#screen -S community-server -X quit
screen -S sempre-server -dm bash -c "./run @mode=interactive; exec sh"
screen -S community-server -dm bash -c "./run @mode=int-utils @cmd=community; exec sh"
echo ./run @mode=int-utils @cmd=simulator
