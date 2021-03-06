package org.cryptomator.frontend.webdav.mount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

class LinuxGioMounter extends VfsMountingStrategy implements MounterStrategy {

	private static final Logger LOG = LoggerFactory.getLogger(LinuxGioMounter.class);
	private static final String DEFAULT_GIO_SCHEME = "dav";
	private static final boolean IS_OS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");

	@Override
	public boolean isApplicable() {
		if (!IS_OS_LINUX) {
			// fail fast (non-blocking)
			return false;
		}

		assert IS_OS_LINUX;
		if( System.getenv().getOrDefault("XDG_CURRENT_DESKTOP", "").equals("KDE")) {
			return false;	//see https://github.com/cryptomator/cryptomator/issues/1381
		}

		// check if gio is installed:
		try {
			ProcessBuilder checkDependenciesCmd = new ProcessBuilder("test", " `command -v gio`");
			ProcessUtil.assertExitValue(ProcessUtil.startAndWaitFor(checkDependenciesCmd, 500, TimeUnit.MILLISECONDS), 0);
			return true;
		} catch (CommandFailedException e) {
			return false;
		}
	}

	@Override
	public Mount mount(URI uri, MountParams mountParams) throws CommandFailedException {
		try {
			URI schemeCorrectedUri = new URI(mountParams.getOrDefault(MountParam.PREFERRED_GVFS_SCHEME, DEFAULT_GIO_SCHEME), uri.getSchemeSpecificPart(), null);
			ProcessBuilder mountCmd = new ProcessBuilder("sh", "-c", "gio mount \"" + schemeCorrectedUri.toASCIIString() + "\"");
			ProcessUtil.assertExitValue(ProcessUtil.startAndWaitFor(mountCmd, 5, TimeUnit.SECONDS), 0);
			LOG.debug("Mounted {}", schemeCorrectedUri.toASCIIString());
			return new MountGioImpl(schemeCorrectedUri);
		} catch (URISyntaxException e) {
			throw new IllegalStateException("URI constructed from elements known to be valid.", e);
		}
	}

	private class MountGioImpl extends MountImpl implements Mount {

		private MountGioImpl(URI uri) {
			this.revealCmd = new ProcessBuilder("sh", "-c", "gio open \"" + uri.toASCIIString() + "\"");
			this.isMountedCmd = new ProcessBuilder("sh", "-c", "test `gio mount --list | grep \"" + uri.toASCIIString() + "\" | wc -l` -eq 1");
			this.unmountCmd = new ProcessBuilder("sh", "-c", "gio mount -u \"" + uri.toASCIIString() + "\"");
			this.uri = uri;
		}

	}

}
