package com.yijinliu.jcrawler;

import java.util.ArrayList;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.ryanharter.auto.value.gson.GenerateTypeAdapter;

@AutoValue
abstract class DownloadedFile {
  public abstract String url();
  public abstract String file();

  public String toJson() {
      return new GsonBuilder()
          .registerTypeAdapterFactory(GenerateTypeAdapter.FACTORY)
          .create()
          .toJson(this);
  }

  public static String toJson(ArrayList<DownloadedFile> dfs) {
      return new GsonBuilder()
          .registerTypeAdapterFactory(GenerateTypeAdapter.FACTORY)
          .create()
          .toJson(dfs);
  }

  public static Builder builder() {
    return new AutoValue_DownloadedFile.Builder();
  }

  @AutoValue.Builder
  abstract interface Builder {
    Builder setUrl(String url);
    Builder setFile(String file);
    DownloadedFile build();
  }

  public static TypeAdapter<DownloadedFile> typeAdapter(Gson gson) {
    return new AutoValue_DownloadedFile.GsonTypeAdapter(gson);
  }
}
