# ANON Film/Dizi — CloudStream eklentisi

Kişisel kullanım içindir. Telifli içerik yüklenmez; sitelerin yayınladığı embed/akış bağlantıları okunur.

## Kurulum

1. CloudStream pre-release APK: https://github.com/recloudstream/cloudstream/releases/download/pre-release/app-prerelease-release.apk
2. Uygulama → Extensions → Repositories → "+" → bu repo URL'sini ekle:

```
https://raw.githubusercontent.com/SamulVanunu/cloudstream-tr/master/repo.json
```

3. "ANON Film/Dizi" reposundan eklentiyi yükle.

## Kaynaklar

- **Filmdozu + Lovefilm (Vidmoly):** 509 film embed'i; oynatma anında cihaz IP'sinden çözülür.
- **Toplu Siteler (142):** verilen domain'ler durumlarıyla listelenir; ölü/engelli olanlar boş döner, çalışanlarda doğrudan m3u8/mp4 veya vidmoly embed aranır.

## Geliştirme

Şablon: `recloudstream/TestPlugins@d87d65a16e1a1a7e895993b4e254dc00f0519dff`

```bash
./gradlew make makePluginsJson
```

Build çıktısı `build/plugins.json` ve modül `.cs3` dosyaları `builds` dalına gider (GitHub Actions).
