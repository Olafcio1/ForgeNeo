package pl.olafcio.forgeneo.loader;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileParser;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.slf4j.Logger;

import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.Manifest;

public final class NeoJarMetadata implements JarMetadata {
    private IModFile modFile;
    private ModuleDescriptor descriptor;

    public static final Manifest DEFAULTMANIFEST;
    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        DEFAULTMANIFEST = new Manifest();
        DEFAULTMANIFEST.getMainAttributes().putValue("FMLModType", "MOD");
    }

    // TODO: Remove helper functions to cleanup api
    @Deprecated(forRemoval = true, since="1.18")
    static Optional<IModFile> buildFile(IModLocator locator, Predicate<SecureJar> jarTest, BiPredicate<String, String> filter, Path... files) {
        return buildFile(j->new ModFile(j, locator, ModFileParser::modsTomlParser), jarTest, filter, files);
    }

    // TODO: Remove helper functions to cleanup api
    @Deprecated(forRemoval = true, since="1.18")
    static IModFile buildFile(IModLocator locator, Path... files) {
        return buildFile(locator, j->true, null, files).orElseThrow(()->new IllegalArgumentException("Failed to find valid JAR file"));
    }

    // TODO: Remove helper functions to cleanup api
    @Deprecated(forRemoval = true, since="1.18")
    static Optional<IModFile> buildFile(Function<SecureJar, IModFile> mfConstructor, Predicate<SecureJar> jarTest, BiPredicate<String, String> filter, Path... files) {
        var mjm = new NeoJarMetadata();
        var sj = SecureJar.from(() -> DEFAULTMANIFEST, j -> mjm, filter, files);
        if (jarTest.test(sj)) {
            var mf = mfConstructor.apply(sj);
            mjm.setModFile(mf);
            return Optional.of(mf);
        } else {
            return Optional.empty();
        }
    }

    NeoJarMetadata() {
    }

    public void setModFile(IModFile file) {
        this.modFile = file;
    }

    @Override
    public String name() {
        return modFile.getModFileInfo().moduleName();
    }

    @Override
    public String version() {
        return modFile.getModFileInfo().versionString();
    }

    @Override
    public ModuleDescriptor descriptor() {
        if (descriptor != null) return descriptor;
        var bld = ModuleDescriptor.newAutomaticModule(name())
                .version(version())
                .packages(modFile.getSecureJar().getPackages());
        modFile.getSecureJar().getProviders().stream()
                .filter(p -> !p.providers().isEmpty())
                .forEach(p -> bld.provides(p.serviceName(), p.providers()));
        modFile.getModFileInfo().usesServices().forEach(bld::uses);
        descriptor = bld.build();
        return descriptor;
    }

    public IModFile modFile() {
        return modFile;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (NeoJarMetadata) obj;
        return Objects.equals(this.modFile, that.modFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modFile);
    }

    @Override
    public String toString() {
        return "NeoJarMetadata[" +"modFile=" + modFile + ']';
    }
}
