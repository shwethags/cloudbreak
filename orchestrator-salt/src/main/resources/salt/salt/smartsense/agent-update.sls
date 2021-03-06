install-smartsense-agent:
  pkg.installed:
   - name: smartsense-hst

update-smartsense-agent:
  cmd.run:
    - name: yum -q -y update http://s3.amazonaws.com/dev.hortonworks.com/hst/centos7/smartsense-hst-1.4.1.2.5.0.1-1817.x86_64.rpm
    - unless: test -f /usr/hdp/share/hst/ambari-service/SMARTSENSE/configuration/product-info.xml

upgrade-smartsense-agent-ambari-agent-cache:
    file.copy:
    - name:  /var/lib/ambari-agent/cache/stacks/HDP/2.1/services/SMARTSENSE
    - force: True
    - mode: 755
    - source: /usr/hdp/share/hst/ambari-service/SMARTSENSE
    - unless: test -f /var/lib/ambari-agent/cache/stacks/HDP/2.1/services/SMARTSENSE/configuration/product-info.xml

disable-hst-gateway-on-agent:
  cmd.run:
    - name: chkconfig hst-gateway off

disable-hst:
  cmd.run:
    - name: chkconfig hst off