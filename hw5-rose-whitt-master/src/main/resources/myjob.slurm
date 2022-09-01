#!/bin/bash

#SBATCH --job-name=comp322-hw5
#SBATCH --nodes=1
#SBATCH --ntasks-per-node=1
#SBATCH --cpus-per-task=16
#SBATCH --mem=16000m
#SBATCH --time=00:30:00
#SBATCH --mail-user=rew9@rice.edu # TODO: Input your net id
#SBATCH --mail-type=ALL
#SBATCH --export=ALL
#SBATCH --partition=scavenge
#SBATCH --exclusive
 
echo "My job ran on:"
pwd 

echo SLURM_NODELIST=$SLURM_NODELIST
echo USER=$USER

export HW5_FOLDER=/home/$USER/hw5-rose-whitt # TODO: Update this path to point to your hw 5 folder

if [[ ! -d $HW5_FOLDER ]]; then
    echo "The designated HW 5 folder ($HW5_FOLDER) does not seem to exist"
    exit 1
fi

cd $HW5_FOLDER

source  /home/mjoyner/comp322/322_setup.sh
java -version
mvn --version

mvn -Dtest=Homework5PerformanceTest clean compile test
