#!/bin/bash
echo "#!/bin/bash" > aws-delete-volumes.sh
for volume in `aws ec2 describe-volumes --filter "Name=status,Values=available" --query "Volumes[*].{ID:VolumeId}" --output text`
do
	echo "found volume $volume"
	echo "echo deleting $volume" >> aws-delete-volumes.sh
	echo "aws ec2 delete-volume --volume-id $volume" >> aws-delete-volumes.sh
done
chmod a+x aws-delete-volumes.sh
echo "Execute ./aws-delete-volumes.sh to delete volumes"

