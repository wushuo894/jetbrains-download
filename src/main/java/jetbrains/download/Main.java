package jetbrains.download;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.StreamProgress;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.log.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Main {
    public static final Log LOG = Log.get(Main.class);

    public static void main(String[] args) {
        File configJsonFile = new File("config.json");
        Assert.isTrue(configJsonFile.exists(), "NO_CONFIG_JSON_FOUND");

        Gson gson = new Gson();

        JsonObject jsonObject = gson.fromJson(FileUtil.readUtf8String(configJsonFile), JsonObject.class);

        String version = jsonObject.get("version").getAsString();
        Map<String, String> tabs = Map.of(
                "mac-arm", "aarch64.dmg",
                "mac-x86", ".dmg",
                "windows-x86", ".exe",
//                "windows-arm", "aarch64.exe",
                "linux-x86", ".tar.gz"
//                "linux-arm", "aarch64.tar.gz"
        );
        String downloadPath = jsonObject.get("downloadPath").getAsString();

        JsonArray jsonElements = jsonObject.getAsJsonArray("downloads");
        Set<String> downloads = jsonElements.asList()
                .stream()
                .map(JsonElement::getAsString)
                .collect(Collectors.toSet());

        tabs.forEach((k, v) -> {
            for (String download : downloads) {
                String downloadUrl = StrFormatter.format(
                        "{}-{}{}",
                        download, version, v.startsWith(".") ? v : "-" + v
                );
                List<String> split = StrUtil.split(downloadUrl, "/");
                File file = new File(StrUtil.join(File.separator, downloadPath, k, version, split.get(split.size() - 1)));
                download(file, downloadUrl);
                System.out.println(downloadUrl);
            }
        });
    }

    public static void download(File file, String downloadUrl) {
        if (file.exists()) {
            System.out.println(StrFormatter.format("{} 文件已存在！", file));
            return;
        }
        AtomicReference<InputStream> inputStream = new AtomicReference<>();
        AtomicReference<OutputStream> outputStream = new AtomicReference<>();

        System.out.println(file);
        System.out.println(downloadUrl);
        File tempFile = new File(file + ".temp");
        if (tempFile.exists()) {
            FileUtil.del(tempFile);
        }
        try {
            HttpRequest.get(downloadUrl)
                    .setFollowRedirects(true)
//                    .setHttpProxy("192.168.196.77",8888)
                    .then(res -> {
                        int status = res.getStatus();

                        if (status != 200) {
                            System.out.println("status: " + status);
                            return;
                        }

                        inputStream.set(res.bodyStream());
                        long contentLength = res.contentLength();
                        outputStream.set(FileUtil.getOutputStream(tempFile));
                        IoUtil.copy(inputStream.get(), outputStream.get(), 81920, new StreamProgress() {
                            @Override
                            public void start() {
                                System.out.println(StrFormatter.format("开始下载：{}", downloadUrl));
                            }

                            @Override
                            public void progress(long total, long progressSize) {
                                double v = ((progressSize * 1.0) / contentLength) * 100;
                                System.out.print(StrFormatter.format("\r{}/{}\t{}%",
                                        contentLength,
                                        progressSize,
                                        NumberUtil.decimalFormat("#.##", v)));
                            }

                            @Override
                            public void finish() {
                            }
                        });
                        System.out.println();
                        if (contentLength != tempFile.length()) {
                            System.out.println("下载失败: " + downloadUrl);
                            return;
                        }
                        System.out.println(StrFormatter.format("下载完成：{}", downloadUrl));
                        FileUtil.rename(tempFile, file.getName(), false);
                    });
        } catch (Exception e) {
            LOG.error(e);
        } finally {
            IoUtil.close(inputStream.get());
            IoUtil.close(outputStream.get());
        }

    }

}
