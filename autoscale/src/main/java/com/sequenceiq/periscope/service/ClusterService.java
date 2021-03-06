package com.sequenceiq.periscope.service;

import static com.sequenceiq.periscope.api.model.ClusterState.RUNNING;
import static org.springframework.util.StringUtils.isEmpty;

import java.util.List;
import java.util.stream.StreamSupport;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;

import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.sequenceiq.periscope.api.model.ClusterState;
import com.sequenceiq.periscope.api.model.ScalingConfigurationJson;
import com.sequenceiq.periscope.domain.Ambari;
import com.sequenceiq.periscope.domain.Cluster;
import com.sequenceiq.periscope.domain.PeriscopeUser;
import com.sequenceiq.periscope.domain.SecurityConfig;
import com.sequenceiq.periscope.model.AmbariStack;
import com.sequenceiq.periscope.repository.ClusterRepository;
import com.sequenceiq.periscope.repository.SecurityConfigRepository;
import com.sequenceiq.periscope.repository.UserRepository;

@Service
public class ClusterService {

    @Inject
    private ClusterRepository clusterRepository;

    @Inject
    private UserRepository userRepository;

    @Inject
    private SecurityConfigRepository securityConfigRepository;

    @Inject
    private AlertService alertService;

    public Cluster create(PeriscopeUser user, AmbariStack stack, ClusterState clusterState) {
        PeriscopeUser periscopeUser = createUserIfAbsent(user);
        validateClusterUniqueness(stack);
        Cluster cluster = new Cluster(periscopeUser, stack);
        if (clusterState != null) {
            cluster.setState(clusterState);
        }
        cluster = save(cluster);
        if (stack.getSecurityConfig() != null) {
            SecurityConfig securityConfig = stack.getSecurityConfig();
            securityConfig.setCluster(cluster);
            securityConfigRepository.save(securityConfig);
        }
//        alertService.addPeriscopeAlerts(cluster);
        return cluster;
    }

    public Cluster update(long clusterId, AmbariStack stack) {
        return update(clusterId, stack, true, null);
    }

    public Cluster update(long clusterId, AmbariStack stack, boolean withPermissionCheck, ClusterState clusterState) {
        Cluster cluster = withPermissionCheck ? findOneById(clusterId) : find(clusterId);
        ClusterState newState = clusterState != null ? clusterState : cluster.getState();
        cluster.setState(newState);
        cluster.update(stack);
        SecurityConfig sSecConf = stack.getSecurityConfig();
        if (sSecConf != null) {
            SecurityConfig updatedConfig = sSecConf;
            SecurityConfig securityConfig = securityConfigRepository.findByClusterId(clusterId);
            if (securityConfig != null) {
                securityConfig.update(updatedConfig);
                securityConfigRepository.save(securityConfig);
            } else {
                SecurityConfig sc = new SecurityConfig(sSecConf.getClientKey(), sSecConf.getClientCert(), sSecConf.getServerCert());
                sc.setCluster(cluster);
                sc = securityConfigRepository.save(sc);
                cluster.setSecurityConfig(sc);
            }
        }
        cluster = save(cluster);
        addPrometheusAlertsToConsul(cluster);
        return cluster;
    }

    public List<Cluster> findAllByUser(PeriscopeUser user) {
        return clusterRepository.findAllByUser(user.getId());
    }

    public Cluster findOneById(long clusterId) {
        return clusterRepository.findOne(clusterId);
    }

    public Cluster save(Cluster cluster) {
        return clusterRepository.save(cluster);
    }

    public Cluster find(long clusterId) {
        return clusterRepository.find(clusterId);
    }

    public void removeOne(long clusterId) {
        Cluster cluster = findOneById(clusterId);
        clusterRepository.delete(cluster);
    }

    public void removeById(long clusterId) {
        Cluster cluster = find(clusterId);
        clusterRepository.delete(cluster);
    }

    public void updateScalingConfiguration(long clusterId, ScalingConfigurationJson scalingConfiguration) {
        Cluster cluster = findOneById(clusterId);
        cluster.setMinSize(scalingConfiguration.getMinSize());
        cluster.setMaxSize(scalingConfiguration.getMaxSize());
        cluster.setCoolDown(scalingConfiguration.getCoolDown());
        save(cluster);
    }

    public ScalingConfigurationJson getScalingConfiguration(long clusterId) {
        Cluster cluster = findOneById(clusterId);
        ScalingConfigurationJson configuration = new ScalingConfigurationJson();
        configuration.setCoolDown(cluster.getCoolDown());
        configuration.setMaxSize(cluster.getMaxSize());
        configuration.setMinSize(cluster.getMinSize());
        return configuration;
    }

    public Cluster setState(long clusterId, ClusterState state) {
        Cluster cluster = findOneById(clusterId);
        cluster.setState(state);
        addPrometheusAlertsToConsul(cluster);
        return clusterRepository.save(cluster);
    }

    public List<Cluster> findAll(ClusterState state) {
        return clusterRepository.findAllByState(state);
    }

    public List<Cluster> findAll() {
        return Lists.newArrayList(clusterRepository.findAll());
    }

    private PeriscopeUser createUserIfAbsent(PeriscopeUser user) {
        PeriscopeUser periscopeUser = userRepository.findOne(user.getId());
        if (periscopeUser == null) {
            periscopeUser = userRepository.save(user);
        }
        return periscopeUser;
    }

    private void addPrometheusAlertsToConsul(Cluster cluster) {
        if (RUNNING.equals(cluster.getState())) {
            alertService.addPrometheusAlertsToConsul(cluster);
        }
    }

    private void validateClusterUniqueness(AmbariStack stack) {
        Iterable<Cluster> clusters = clusterRepository.findAll();
        boolean clusterForTheSameStackAndAmbari = StreamSupport.stream(clusters.spliterator(), false)
                .anyMatch(cluster -> {
                    boolean equalityOfStackId = cluster.getStackId() != null && cluster.getStackId().equals(stack.getStackId());
                    Ambari ambari = cluster.getAmbari();
                    Ambari newAmbari = stack.getAmbari();
                    boolean ambariObjectsNotNull = ambari != null && newAmbari != null;
                    boolean ambariHostsNotEmpty = !isEmpty(ambari.getHost()) && !isEmpty(newAmbari.getHost());
                    boolean equalityOfAmbariHost = ambariObjectsNotNull && ambariHostsNotEmpty && ambari.getHost().equals(newAmbari.getHost());
                    return equalityOfStackId && equalityOfAmbariHost;
                });
        if (clusterForTheSameStackAndAmbari) {
            throw new BadRequestException("Cluster exists for the same Cloudbreak stack id and Ambari host.");
        }
    }

}
