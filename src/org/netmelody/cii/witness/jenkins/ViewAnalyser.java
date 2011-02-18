package org.netmelody.cii.witness.jenkins;

import static com.google.common.collect.Collections2.transform;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.netmelody.cii.domain.Status;
import org.netmelody.cii.domain.Target;
import org.netmelody.cii.witness.jenkins.jsondomain.Job;
import org.netmelody.cii.witness.jenkins.jsondomain.View;

import com.google.common.base.Function;

public class ViewAnalyser {

    private final JenkinsCommunicator communicator;
    private final Map<String, JobAnalyser> analyserMap = new HashMap<String, JobAnalyser>();
    
    public ViewAnalyser(JenkinsCommunicator communicator) {
        this.communicator = communicator;
    }
    
    public Collection<Target> analyse(View viewDigest) {
        return transform(jobsFor(viewDigest), new Function<Job, Target>() {
            @Override public Target apply(Job job) {
                return targetFrom(job);
            }
        });
    }
    
    private Collection<Job> jobsFor(View viewDigest) {
        return communicator.makeJenkinsRestCall(viewDigest.url, View.class).jobs;
    }
    
    private Target targetFrom(Job jobDigest) {
        if (!jobDigest.building() && Status.BROKEN != jobDigest.status()) {
            analyserMap.remove(jobDigest.url);
            return new Target(jobDigest.url, jobDigest.name, jobDigest.status());
        }
        
        if (!analyserMap.containsKey(jobDigest.url)) {
            analyserMap.put(jobDigest.url, new JobAnalyser(communicator, jobDigest.url));
        }
        return analyserMap.get(jobDigest.url).analyse();
    }
}