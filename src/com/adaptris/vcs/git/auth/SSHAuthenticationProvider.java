package com.adaptris.vcs.git.auth;

import java.io.File;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.util.FS;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SSHAuthenticationProvider implements AuthenticationProvider {

  private String passPhrase;
  
  private File privateKeyFile;
  
  public SSHAuthenticationProvider() {
  }
  
  public SSHAuthenticationProvider(String passPhrase, File provateKeyFile) {
    this.setPassPhrase(passPhrase);
    this.setPrivateKeyFile(provateKeyFile);
  }
  
  @Override
  public CredentialsProvider getCredentialsProvider() {
    return null;
  }

  @Override
  public TransportConfigCallback getTransportInterceptor() {
    final SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
      @Override
      protected void configure(Host host, Session session) {
        session.setPassword(getPassPhrase());
      }
      
      @Override
      protected JSch createDefaultJSch(FS fs) throws JSchException {
        JSch defaultJSch = super.createDefaultJSch(fs);
        defaultJSch.addIdentity(getPrivateKeyFile().getAbsolutePath());
        return defaultJSch;
      }
    };

    return new TransportConfigCallback() {
      @Override
      public void configure(Transport transport) {
        SshTransport sshTransport = (SshTransport) transport;
        sshTransport.setSshSessionFactory(sshSessionFactory);
      }
    };
  }

  public String getPassPhrase() {
    return passPhrase;
  }

  public void setPassPhrase(String passPhrase) {
    this.passPhrase = passPhrase;
  }

  public File getPrivateKeyFile() {
    return privateKeyFile;
  }

  public void setPrivateKeyFile(File privateKeyFile) {
    this.privateKeyFile = privateKeyFile;
  }
}
