package pl.olafcio.forgeneo.loader;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LogMarkers;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;
import net.minecraftforge.fml.loading.StringUtils;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileParser;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import net.minecraftforge.forgespi.locating.ModFileLoadingException;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.olafcio.forgeneo.loader.class_transformer.NeoClassTransformer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public class NeoModLocator extends AbstractJarFileModLocator {
    private static final String SUFFIX = ".jar";
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoModLocator.class);  // Look I am not going to use minecraft libraries
                                                                                        // at coremodding time WHAT THE FUCK

    private final Path modFolder;

    public NeoModLocator() {
        this.modFolder = FMLPaths.MODSDIR.get();
    }

    @Override
    public String name() {
        return "neoforge loader";
    }

    @Override
    public Stream<Path> scanCandidates() {
        LOGGER.debug(LogMarkers.SCAN, "Scanning mods dir {} for NeoForge mods", this.modFolder);
        var excluded = ModDirTransformerDiscoverer.allExcluded();

        return uncheck(() -> Files.list(this.modFolder))
                .filter(p -> !excluded.contains(p) && StringUtils.toLowerCase(p.getFileName().toString()).endsWith(SUFFIX))
                .sorted(Comparator.comparing(path -> StringUtils.toLowerCase(path.getFileName().toString())));
    }

    protected static final String MODS_TOML = "META-INF/neoforge.mods.toml";
    static final Attributes.Name TYPE = new Attributes.Name("FMLModType");

    @Override
    protected ModFileOrException createMod(Path... paths) {
        var path = transformMod(paths);

        var mjm = new NeoJarMetadata();
        var sj = SecureJar.from(
                Manifest::new,
                jar -> jar.moduleDataProvider().findFile(MODS_TOML).isPresent() ? mjm : JarMetadata.from(jar, path),
                path
        );

        IModFile mod;
        var type = sj.moduleDataProvider().getManifest().getMainAttributes().getValue(TYPE);
        if (type == null)
            type = getDefaultJarModType();

        if (sj.moduleDataProvider().findFile(MODS_TOML).isPresent()) {
            LOGGER.debug(LogMarkers.SCAN, "Found {} neoforge mod of type {}: {}", MODS_TOML, type, path);
            mod = new ModFile(sj, this, ModFileParser::modsTomlParser);
        } else if (type != null) {
            LOGGER.debug(LogMarkers.SCAN, "Found {} neoforge mod of type {}: {}", MANIFEST, type, path);
            mod = new ModFile(sj, this, this::manifestParser, type);
        } else
            return new IModLocator.ModFileOrException(null, new ModFileLoadingException("Invalid mod file found " + Arrays.toString(path)));

        mjm.setModFile(mod);
        return new IModLocator.ModFileOrException(mod, null);
    }

    private static Path @NotNull [] transformMod(Path[] paths) {
        var directory = Path.of("./.cache/forge_neo/transformed_jars");
        var outputPaths = new Path[paths.length];

        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException("[ForgeNeo] Failed to create .cache/forge_neo/transformed_jars directory  /IOException", e);
        }

        int i = 0;

        for (var path : paths) {
            var outputPath = directory.resolve(path.getFileName());

            outputPaths[i++] = outputPath;

            try {
                var fos = new FileOutputStream(outputPath.toFile());
                var zos = new ZipOutputStream(fos);

                try (var zip = new ZipFile(path.toFile())) {
                    var entries = zip.entries();
                    var transformer = new NeoClassTransformer();

                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        if (entry.getName().endsWith(".class")) {
                            var node = new ClassNode();

                            try (var stream = zip.getInputStream(entry)) {
                                var reader = new ClassReader(stream) {
                                    @Override
                                    public String readClass(int offset, char[] charBuffer) {
                                        var str = super.readClass(offset, charBuffer);

                                        // LOL
                                        if (str.startsWith("net.neoforged.neoforge.")) {
                                            str = "net.minecraftforge." + str.substring(23);
                                        } else if (str.startsWith("net.neoforged.accesstransformer.")) {
                                            str = "net.minecraftforge.accesstransformer." + str.substring(32);
                                        } else if (str.startsWith("net.neoforged.bus.")) {
                                            str = "net.minecraftforge.eventbus." + str.substring(18);
                                        }

                                        return str;
                                    }
                                };

                                reader.accept(node, 0);
                            }

                            transformer.transform(node);

                            var writer = new ClassWriter(0);
                            node.accept(writer);

                            zos.putNextEntry(entry);
                            zos.write(writer.toByteArray());
                        } else {
                            zos.putNextEntry(entry);

                            try (var stream = zip.getInputStream(entry)) {
                                stream.transferTo(zos);
                            }
                        }
                    }
                }

                zos.close();
                fos.close();
            } catch (ZipException e) {
                throw new RuntimeException("[ForgeNeo] Failed to load NeoForge mod '%s'  /ZipException".formatted(path.toAbsolutePath()), e);
            } catch (IOException e) {
                throw new RuntimeException("[ForgeNeo] Failed to load NeoForge mod '%s'  /IOException".formatted(path.toAbsolutePath()), e);
            }
        }

        return outputPaths;
    }

    @Override
    public void initArguments(Map<String, ?> map) {
    }
}
