package org.netmelody.cieye.spies.jenkins;

import static com.google.common.collect.Iterables.find;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.netmelody.cieye.core.domain.Feature;
import org.netmelody.cieye.core.domain.TargetDetail;
import org.netmelody.cieye.core.domain.TargetDigest;
import org.netmelody.cieye.core.domain.TargetDigestGroup;
import org.netmelody.cieye.core.domain.TargetId;
import org.netmelody.cieye.core.observation.CiSpy;
import org.netmelody.cieye.core.observation.CommunicationNetwork;
import org.netmelody.cieye.core.observation.KnownOffendersDirectory;
import org.netmelody.cieye.spies.jenkins.jsondomain.Job;
import org.netmelody.cieye.spies.jenkins.jsondomain.View;

import com.google.common.base.Predicate;

public final class JenkinsSpy implements CiSpy {
    
    private static final Log LOG = LogFactory.getLog(JenkinsSpy.class);
    
    private final JenkinsCommunicator communicator;
    private final JobLaboratory laboratory;
    
    private final Map<TargetId, Job> recognisedJobs = newHashMap();
    
    public JenkinsSpy(String endpoint, CommunicationNetwork network, KnownOffendersDirectory detective) {
        this.communicator = new JenkinsCommunicator(endpoint, "ci", "", network.makeContact(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")));
        this.laboratory = new JobLaboratory(communicator, detective);
    }

    @Override
    public TargetDigestGroup targetsConstituting(Feature feature) {
        final Collection<Job> jobs = jobsFor(feature);
        final List<TargetDigest> digests = newArrayList();
        
        for (Job job : jobs) {
            final TargetDigest targetDigest = new TargetDigest(job.url, job.url, job.name, job.status());
            digests.add(targetDigest);
            recognisedJobs.put(targetDigest.id(), job);
        }
        
        return new TargetDigestGroup(digests);
    }

    @Override
    public TargetDetail statusOf(final TargetId target) {
        Job job = recognisedJobs.get(target);
        if (null == job) {
            return null;
        }
        return laboratory.analyseJob(job);
    }

    @Override
    public boolean takeNoteOf(TargetId target, String note) {
        if (!recognisedJobs.containsKey(target)) {
            return false;
        }
        
        final Job job = this.recognisedJobs.get(target);
        final String buildUrl = this.laboratory.lastBadBuildUrlFor(job);
        
        if (buildUrl.isEmpty()) {
            return true;
        }
        
        communicator.doJenkinsPost(buildUrl +
                                   "submitDescription?" +
                                   URLEncodedUtils.format(newArrayList(new BasicNameValuePair("description", note)), "UTF-8"));
        return true;
    }
    
    private Collection<Job> jobsFor(final Feature feature) {
        if (!communicator.canSpeakFor(feature)) {
            return newArrayList();
        }
        
        final View viewDigest = find(communicator.views(), withName(feature.name()), null);
        if (null == viewDigest) {
            LOG.error("No view named <" + feature.name() + "> found");
            return newArrayList();
        }
        
        return communicator.jobsFor(viewDigest);
    }

    private Predicate<View> withName(final String featureName) {
        return new Predicate<View>() {
            @Override public boolean apply(View viewDigest) {
                return viewDigest.name.trim().equals(featureName.trim());
            }
        };
    }
}
