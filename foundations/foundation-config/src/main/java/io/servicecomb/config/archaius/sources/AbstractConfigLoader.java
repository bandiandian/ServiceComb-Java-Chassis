/*
 * Copyright 2017 Huawei Technologies Co., Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicecomb.config.archaius.sources;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.ResourceUtils;

public abstract class AbstractConfigLoader {
  protected String orderKey = "config-order";

  public void setOrderKey(String orderKey) {
    this.orderKey = orderKey;
  }

  protected final List<ConfigModel> configModels = new ArrayList<>();

  public List<ConfigModel> getConfigModels() {
    return configModels;
  }

  public void load(String resourceName) throws IOException {
    loadFromClassPath(resourceName);
  }

  protected void loadFromClassPath(String resourceName) throws IOException {
    List<URL> urlList = findURLFromClassPath(resourceName);
    for (URL url : urlList) {
      ConfigModel configModel = load(url);
      configModels.add(configModel);
    }
  }

  public ConfigModel load(URL url) throws IOException {
    Map<String, Object> config = loadData(url);
    // load a empty or all commented yaml, will get a null map
    // this is not a error
    if (config == null) {
      config = new LinkedHashMap<>();
    }

    ConfigModel configModel = new ConfigModel();
    configModel.setUrl(url);
    configModel.setConfig(config);
    Object objOrder = config.get(orderKey);
    if (objOrder != null) {
      if (Integer.class.isInstance(objOrder)) {
        configModel.setOrder((int) objOrder);
      } else {
        configModel.setOrder(Integer.parseInt(String.valueOf(objOrder)));
      }
    }
    return configModel;
  }

  protected abstract Map<String, Object> loadData(URL url) throws IOException;

  protected List<URL> findURLFromClassPath(String resourceName) throws IOException {
    List<URL> urlList = new ArrayList<>();

    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    Enumeration<URL> urls = loader.getResources(resourceName);
    while (urls.hasMoreElements()) {
      urlList.add(urls.nextElement());
    }

    return urlList;
  }

  private class ConfigModelWrapper {
    ConfigModel model;

    int addOrder;
  }

  // sort rule:
  // 1.files in jar
  // 2.smaller order
  // 3.add to list earlier
  protected void sort() {
    List<ConfigModelWrapper> list = new ArrayList<>(configModels.size());
    for (int idx = 0; idx < configModels.size(); idx++) {
      ConfigModelWrapper wrapper = new ConfigModelWrapper();
      wrapper.model = configModels.get(idx);
      wrapper.addOrder = idx;
      list.add(wrapper);
    }

    list.sort(this::doSort);

    for (int idx = 0; idx < configModels.size(); idx++) {
      configModels.set(idx, list.get(idx).model);
    }
  }

  private int doSort(ConfigModelWrapper w1, ConfigModelWrapper w2) {
    ConfigModel m1 = w1.model;
    ConfigModel m2 = w2.model;
    boolean isM1Jar = ResourceUtils.isJarURL(m1.getUrl());
    boolean isM2Jar = ResourceUtils.isJarURL(m2.getUrl());
    if (isM1Jar != isM2Jar) {
      if (isM1Jar) {
        return -1;
      }

      return 1;
    }
    // min order load first
    int result = m1.getOrder() - m2.getOrder();
    if (result != 0) {
      return result;
    }

    return doFinalSort(w1, w2);
  }

  private int doFinalSort(ConfigModelWrapper w1, ConfigModelWrapper w2) {
    return w1.addOrder - w2.addOrder;
  }
}
