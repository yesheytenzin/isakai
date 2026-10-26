// SPDX-License-Identifier: GPL-3.0-or-later

// Narrow C ABI over AOSP libandroidfw (AssetManager2) for the JNI resource
// backend in src/bridge/resources.zig. Mirrors the native half of AOSP's
// android_util_AssetManager.cpp / android_util_XmlBlock.cpp: every handle
// here has an explicit delete, and ApkAssets are cached for the process
// lifetime because open Assets keep reading their archive.

#include <androidfw/ApkAssets.h>
#include <androidfw/Asset.h>
#include <androidfw/AssetDir.h>
#include <androidfw/AssetManager2.h>
#include <androidfw/AttributeResolution.h>
#include <androidfw/ResourceTypes.h>
#include <androidfw/ResourceUtils.h>

#include <cstring>
#include <map>
#include <mutex>
#include <string>
#include <unistd.h>
#include <vector>

using android::ApkAssetsCookie;
using android::AssetManager2;

namespace {

std::mutex apk_cache_mutex;
std::map<std::string, AssetManager2::ApkAssetsPtr> apk_cache;

AssetManager2::ApkAssetsPtr LoadApk(const char* path, bool system) {
  std::lock_guard<std::mutex> lock(apk_cache_mutex);
  auto it = apk_cache.find(path);
  if (it != apk_cache.end()) return it->second;
  AssetManager2::ApkAssetsPtr apk =
      android::ApkAssets::Load(path, system ? android::PROPERTY_SYSTEM : 0U);
  if (apk != nullptr) apk_cache.emplace(path, apk);
  return apk;
}

}  // namespace

struct IsakaiRes {
  std::mutex mutex;
  std::vector<AssetManager2::ApkAssetsPtr> apks;
  AssetManager2 am;
};

struct IsakaiTheme {
  IsakaiRes* res;
  std::unique_ptr<android::Theme> theme;
};

struct IsakaiXml {
  android::ResXMLTree tree;
  android::ResXMLParser parser;
  explicit IsakaiXml(std::shared_ptr<const android::DynamicRefTable> table)
      : tree(std::move(table)), parser(tree) {}
};

// cookie is -1 when absent; type/data follow Res_value.
struct IsakaiValue {
  int32_t cookie;
  uint32_t type;
  uint32_t data;
  uint32_t resid;
  uint32_t flags;
  uint32_t density;
};

static void CopyValue(const AssetManager2::SelectedValue& v, IsakaiValue* out) {
  out->cookie = v.cookie;
  out->type = v.type;
  out->data = v.data;
  out->resid = v.resid;
  out->flags = v.flags;
  out->density = v.config.density;
}

extern "C" {

// The first `system_count` paths load as system (framework) archives.
IsakaiRes* isakai_res_open(const char* const* paths, size_t count, size_t system_count) {
  auto res = std::make_unique<IsakaiRes>();
  for (size_t i = 0; i < count; i++) {
    AssetManager2::ApkAssetsPtr apk = LoadApk(paths[i], i < system_count);
    if (apk == nullptr) return nullptr;
    res->apks.push_back(std::move(apk));
  }
  if (!res->am.SetApkAssets(res->apks)) return nullptr;
  return res.release();
}

void isakai_res_close(IsakaiRes* res) { delete res; }

// fields: mcc, mnc, orientation, touchscreen, density, keyboard, inputFlags,
// navigation, screenWidth, screenHeight, smallestScreenWidthDp,
// screenWidthDp, screenHeightDp, screenLayout, uiMode, colorMode, sdkVersion,
// hardKeyboardHidden
void isakai_res_set_config(IsakaiRes* res, const int32_t* f, size_t n, const char* locale) {
  if (n < 18) return;
  android::ResTable_config c{};
  c.size = sizeof(c);
  c.mcc = static_cast<uint16_t>(f[0]);
  c.mnc = static_cast<uint16_t>(f[1]);
  c.orientation = static_cast<uint8_t>(f[2]);
  c.touchscreen = static_cast<uint8_t>(f[3]);
  c.density = static_cast<uint16_t>(f[4]);
  c.keyboard = static_cast<uint8_t>(f[5]);
  c.inputFlags = static_cast<uint8_t>(f[6] | ((f[17] & 0x3) << 4));  // match masks read only the low nibble
  c.navigation = static_cast<uint8_t>(f[7]);
  c.screenWidth = static_cast<uint16_t>(f[8]);
  c.screenHeight = static_cast<uint16_t>(f[9]);
  c.smallestScreenWidthDp = static_cast<uint16_t>(f[10]);
  c.screenWidthDp = static_cast<uint16_t>(f[11]);
  c.screenHeightDp = static_cast<uint16_t>(f[12]);
  c.screenLayout = static_cast<uint8_t>(f[13]);
  c.uiMode = static_cast<uint8_t>(f[14]);
  c.colorMode = static_cast<uint8_t>(f[15]);
  c.sdkVersion = static_cast<uint16_t>(f[16]);
  if (locale != nullptr && locale[0] != '\0') c.setBcp47Locale(locale);
  std::lock_guard<std::mutex> lock(res->mutex);
  res->am.SetConfigurations({c});
}

bool isakai_res_get_value(IsakaiRes* res, uint32_t resid, bool resolve, uint16_t density,
                          IsakaiValue* out) {
  std::lock_guard<std::mutex> lock(res->mutex);
  auto value = res->am.GetResource(resid, false, density);
  if (!value.has_value()) return false;
  if (resolve && !res->am.ResolveReference(*value).has_value()) return false;
  CopyValue(*value, out);
  return true;
}

const char16_t* isakai_res_pool_string(IsakaiRes* res, int32_t cookie, uint32_t index,
                                       size_t* len) {
  std::lock_guard<std::mutex> lock(res->mutex);
  const android::ResStringPool* pool = res->am.GetStringPoolForCookie(cookie);
  if (pool == nullptr) return nullptr;
  auto s = pool->stringAt(index);
  if (!s.has_value()) return nullptr;
  *len = s->size();
  return s->data();
}

// Returns the entry count (writing at most `cap` entries) or -1. Values are
// reference-resolved like AOSP getResourceStringArray/IntArray.
int32_t isakai_res_get_bag(IsakaiRes* res, uint32_t resid, uint32_t* keys, IsakaiValue* values,
                           size_t cap) {
  std::lock_guard<std::mutex> lock(res->mutex);
  auto bag = res->am.GetBag(resid);
  if (!bag.has_value()) return -1;
  const android::ResolvedBag* b = *bag;
  for (uint32_t i = 0; i < b->entry_count && i < cap; i++) {
    const android::ResolvedBag::Entry& e = b->entries[i];
    AssetManager2::SelectedValue v(b, e);
    if (!res->am.ResolveReference(v).has_value()) {
      v = AssetManager2::SelectedValue(b, e);
    }
    keys[i] = e.key;
    CopyValue(v, &values[i]);
  }
  return static_cast<int32_t>(b->entry_count);
}

// Writes "package:type/entry" (UTF-8, NUL-terminated); returns its length
// or -1 when unknown or too long for `cap`.
int32_t isakai_res_name(IsakaiRes* res, uint32_t resid, char* buf, size_t cap) {
  std::lock_guard<std::mutex> lock(res->mutex);
  auto name = res->am.GetResourceName(resid);
  if (!name.has_value()) return -1;
  std::string s = android::ToFormattedResourceString(*name);
  if (s.size() + 1 > cap) return -1;
  std::memcpy(buf, s.c_str(), s.size() + 1);
  return static_cast<int32_t>(s.size());
}

uint32_t isakai_res_identifier(IsakaiRes* res, const char* name, const char* type,
                               const char* package) {
  std::lock_guard<std::mutex> lock(res->mutex);
  auto id = res->am.GetResourceId(name, type, package);
  return id.has_value() ? *id : 0;
}

IsakaiTheme* isakai_theme_new(IsakaiRes* res) {
  std::lock_guard<std::mutex> lock(res->mutex);
  return new IsakaiTheme{res, res->am.NewTheme()};
}

void isakai_theme_delete(IsakaiTheme* theme) { delete theme; }

bool isakai_theme_apply(IsakaiTheme* theme, uint32_t style, bool force) {
  std::lock_guard<std::mutex> lock(theme->res->mutex);
  return theme->theme->ApplyStyle(style, force).has_value();
}

bool isakai_theme_set_to(IsakaiTheme* dst, const IsakaiTheme* src) {
  std::lock_guard<std::mutex> lock(dst->res->mutex);
  return dst->theme->SetTo(*src->theme).has_value();
}

void isakai_theme_clear(IsakaiTheme* theme) {
  std::lock_guard<std::mutex> lock(theme->res->mutex);
  theme->theme->Clear();
}

bool isakai_theme_attr(IsakaiTheme* theme, uint32_t attr, bool resolve, IsakaiValue* out) {
  std::lock_guard<std::mutex> lock(theme->res->mutex);
  auto value = theme->theme->GetAttribute(attr);
  if (!value.has_value()) return false;
  if (resolve && !theme->res->am.ResolveReference(*value).has_value()) return false;
  CopyValue(*value, out);
  return true;
}

uint32_t isakai_theme_changing_configurations(IsakaiTheme* theme) {
  return theme->theme->GetChangingConfigurations();
}

// out_values holds attrs_length * android::STYLE_NUM_ENTRIES words,
// out_indices attrs_length + 1 (count first), as in AOSP applyStyle.
bool isakai_apply_style(IsakaiTheme* theme, IsakaiXml* xml, uint32_t def_style_attr,
                        uint32_t def_style_res, const uint32_t* attrs, size_t attrs_length,
                        uint32_t* out_values, uint32_t* out_indices) {
  std::lock_guard<std::mutex> lock(theme->res->mutex);
  return android::ApplyStyle(theme->theme.get(), xml == nullptr ? nullptr : &xml->parser,
                             def_style_attr, def_style_res, attrs, attrs_length, out_values,
                             out_indices)
      .has_value();
}

bool isakai_retrieve_attributes(IsakaiRes* res, IsakaiXml* xml, uint32_t* attrs,
                                size_t attrs_length, uint32_t* out_values,
                                uint32_t* out_indices) {
  std::lock_guard<std::mutex> lock(res->mutex);
  return android::RetrieveAttributes(&res->am, &xml->parser, attrs, attrs_length, out_values,
                                     out_indices)
      .has_value();
}

// Opens a compiled XML file from the given cookie (or any ApkAssets when
// cookie is -1). Returns null for a missing or malformed document.
IsakaiXml* isakai_xml_open(IsakaiRes* res, int32_t cookie, const char* path) {
  std::lock_guard<std::mutex> lock(res->mutex);
  ApkAssetsCookie found = cookie;
  std::unique_ptr<android::Asset> asset =
      cookie == android::kInvalidCookie
          ? res->am.OpenNonAsset(path, android::Asset::ACCESS_BUFFER, &found)
          : res->am.OpenNonAsset(path, cookie, android::Asset::ACCESS_BUFFER);
  if (asset == nullptr) return nullptr;
  const void* buffer = asset->getBuffer(true);
  if (buffer == nullptr) return nullptr;
  auto xml = std::make_unique<IsakaiXml>(res->am.GetDynamicRefTableForCookie(found));
  if (xml->tree.setTo(buffer, asset->getLength(), true) != android::NO_ERROR) return nullptr;
  xml->parser.restart();
  return xml.release();
}

void isakai_xml_close(IsakaiXml* xml) { delete xml; }

int32_t isakai_xml_next(IsakaiXml* xml) { return xml->parser.next(); }

// Current event as words: line, nameId, namespaceId, textId, attrCount, then
// per attribute namespaceId, nameId, nameResId, valueStringId, type, data.
// Returns the word count (writing at most `cap`).
size_t isakai_xml_current(IsakaiXml* xml, int32_t* out, size_t cap) {
  android::ResXMLParser& p = xml->parser;
  bool tag = p.getEventType() == android::ResXMLParser::START_TAG;
  size_t count = tag ? p.getAttributeCount() : 0;
  size_t need = 5 + count * 6;
  if (cap < need) return need;
  out[0] = static_cast<int32_t>(p.getLineNumber());
  out[1] = (tag || p.getEventType() == android::ResXMLParser::END_TAG) ? p.getElementNameID() : -1;
  out[2] = (tag || p.getEventType() == android::ResXMLParser::END_TAG) ? p.getElementNamespaceID() : -1;
  out[3] = p.getEventType() == android::ResXMLParser::TEXT ? p.getTextID() : -1;
  out[4] = static_cast<int32_t>(count);
  for (size_t i = 0; i < count; i++) {
    int32_t* a = out + 5 + i * 6;
    android::Res_value v;
    if (p.getAttributeValue(i, &v) < 0) {
      v.dataType = android::Res_value::TYPE_NULL;
      v.data = 0;
    }
    a[0] = p.getAttributeNamespaceID(i);
    a[1] = p.getAttributeNameID(i);
    a[2] = static_cast<int32_t>(p.getAttributeNameResID(i));
    a[3] = p.getAttributeValueStringID(i);
    a[4] = v.dataType;
    a[5] = static_cast<int32_t>(v.data);
  }
  return need;
}

const char16_t* isakai_xml_string(IsakaiXml* xml, uint32_t index, size_t* len) {
  auto s = xml->tree.getStrings().stringAt(index);
  if (!s.has_value()) return nullptr;
  *len = s->size();
  return s->data();
}

android::Asset* isakai_asset_open(IsakaiRes* res, const char* path, int32_t mode,
                                  int32_t* out_cookie) {
  std::lock_guard<std::mutex> lock(res->mutex);
  ApkAssetsCookie cookie = android::kInvalidCookie;
  std::unique_ptr<android::Asset> asset =
      res->am.OpenNonAsset(path, static_cast<android::Asset::AccessMode>(mode), &cookie);
  *out_cookie = cookie;
  return asset.release();
}

int32_t isakai_asset_read(android::Asset* asset, void* buf, size_t count) {
  return static_cast<int32_t>(asset->read(buf, count));
}

int64_t isakai_asset_seek(android::Asset* asset, int64_t offset, int32_t whence) {
  return asset->seek(offset, whence);
}

int64_t isakai_asset_length(android::Asset* asset) { return asset->getLength(); }

int64_t isakai_asset_remaining(android::Asset* asset) { return asset->getRemainingLength(); }

const void* isakai_asset_buffer(android::Asset* asset) {
  return asset->getBuffer(false);
}

bool isakai_asset_is_allocated(android::Asset* asset) { return asset->isAllocated(); }

int isakai_asset_fd(android::Asset* asset, int64_t* start, int64_t* length) {
  off64_t s = 0;
  off64_t l = 0;
  int fd = asset->openFileDescriptor(&s, &l);
  if (fd < 0) return -1;
  *start = s;
  *length = l;
  return fd;
}

// Byte range of an uncompressed entry inside its archive; false when the
// entry is compressed. The caller opens the archive itself.
bool isakai_asset_range(android::Asset* asset, int64_t* start, int64_t* length) {
  off64_t s = 0;
  off64_t l = 0;
  int fd = asset->openFileDescriptor(&s, &l);
  if (fd < 0) return false;
  close(fd);
  *start = s;
  *length = l;
  return true;
}

void isakai_asset_close(android::Asset* asset) { delete asset; }

// Entry names directly under `dir` in the archives' assets/ tree, joined by
// NUL into a malloc'd buffer the caller frees with free(). Null when the
// directory is absent.
char* isakai_asset_list(IsakaiRes* res, const char* dir, size_t* out_len) {
  std::unique_ptr<android::AssetDir> listing;
  {
    std::lock_guard<std::mutex> lock(res->mutex);
    listing = res->am.OpenDir(dir);
  }
  if (listing == nullptr) return nullptr;
  std::string joined;
  for (size_t i = 0; i < listing->getFileCount(); i++) {
    joined.append(listing->getFileName(i).c_str());
    joined.push_back('\0');
  }
  char* out = static_cast<char*>(malloc(joined.size() + 1));
  if (out == nullptr) return nullptr;
  std::memcpy(out, joined.c_str(), joined.size() + 1);  // copies the trailing NUL
  *out_len = joined.size();
  return out;
}

}  // extern "C"
