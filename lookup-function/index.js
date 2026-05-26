const compute = require('@google-cloud/compute');
const instancesClient = new compute.InstancesClient();

exports.getGrpcIp = async (req, res) => {
    const projectId = 'cn2526-t2-g10';

    try {
        const aggListRequest = instancesClient.aggregatedListAsync({
            project: projectId,
        });

        const activeIps = [];

        for await (const [zone, instancesObject] of aggListRequest) {
            const instances = instancesObject.instances;
            if (instances && instances.length > 0) {
                for (const instance of instances) {
                    if (instance.status === 'RUNNING' &&
                        instance.tags &&
                        instance.tags.items &&
                        instance.tags.items.includes('grpc-server')) {

                        const natIp = instance.networkInterfaces[0].accessConfigs[0].natIP;
                        if (natIp) activeIps.push(natIp);
                    }
                }
            }
        }

        if (activeIps.length === 0) {
            return res.status(404).send("No active servers currently running.");
        }

        res.status(200).send(activeIps.join(','));

    } catch (error) {
        console.error(error);
        res.status(500).send("Error fetching instances");
    }
};