package jetbrains.download;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.StreamProgress;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.log.Log;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Main {
    public static final Log LOG = Log.get(Main.class);

    public static void main(String[] args) {
        // 下载位置
        String downloadPath = "/Volumes/wushuo/Software/J/JetBrains";

        Gson gson = new Gson();
        JsonObject jsonObject = HttpRequest.get("https://data.services.jetbrains.com/products/releases")
                .form("code", "IIU,GO,PS,DG,CL,RD,PCP,WS")
                .form("latest", "true")
                .form("type", "release")
                .thenFunction(res -> gson.fromJson(res.body(), JsonObject.class));

        List<String> platforms = List.of("linux", "windows", "mac", "macM1");

        List<JsonObject> list = jsonObject.entrySet()
                .stream()
                .map(Map.Entry::getValue)
                .map(JsonElement::getAsJsonArray)
                .map(it -> it.get(0))
                .map(JsonElement::getAsJsonObject)
                .collect(Collectors.toList());

        for (JsonObject item : list) {
            String version = item.get("version").getAsString();
            JsonObject downloads = item.getAsJsonObject("downloads");
            for (String platform : platforms) {
                JsonObject downloadInfo = downloads.getAsJsonObject(platform);
                String downloadUrl = downloadInfo.get("link").getAsString();
                List<String> split = StrUtil.split(downloadUrl, "/");
                File file = new File(
                        StrUtil.join(File.separator, downloadPath, platform, version, split.get(split.size() - 1))
                );
                download(file, downloadUrl);
            }
        }
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
                                System.out.println();
                                System.out.println(StrFormatter.format("下载完成：{}", downloadUrl));
                            }
                        });
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
