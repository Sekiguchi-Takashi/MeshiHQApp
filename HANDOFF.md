# MeshiHQApp HANDOFF

## これは何
近所の飲食店を自分のDBに貯めて管理するAndroidアプリ。オントロジー「飲食店マップ管理アプリ」の実装第一弾。

## 現在地点
v0.1（地図＋店舗登録＋店舗詳細、手入力のみ）。

## 決定事項
- カテゴリは固定16種（和食／中華／寿司／焼肉／しゃぶしゃぶ／イタリアン／ピザ専門／ファミレス／ラーメン／たこ焼き／うどん／ハンバーガー／アイス／カフェ／居酒屋／その他）。`domain/Model.kt` の `Categories.ALL` が唯一の定義元
- 予算は「1人あたり1000〜3000円、500円刻みの帯」。DBには `budget_*_min` / `budget_*_max` を持ち、詳細画面の人数チップ（1〜6人）で合計を表示する
- エクスポートはBonsaiのRAG資料形式（`type: shop` の frontmatter 付きMarkdown）＋ バックアップ用 `shops.json`。書き出し先はSAFで選んだフォルダ
- BONSAI_API.md は読むだけ。サーバ側の仕様変更はこのチャットからは出さない

## 構成
- Kotlin / Jetpack Compose / Room / osmdroid（APIキー不要）
- 位置情報は標準 LocationManager（Google Play services 非依存）
- v0.1 ではViewModelを置かず、画面Composable内で状態を持つ。画面が太ってきた時点で分離する

## 主要ファイル
- `domain/Model.kt` … カテゴリ・予算・信頼度・距離計算
- `data/db/Entities.kt` … shop / fact_source / pending_change
- `data/repo/ShopRepository.kt` … 保存時に fact_source へ出所を記録（手入力は self_visit 1.0）
- `data/export/BonsaiExport.kt` … Markdown資料とJSONの書き出し・取り込み
- `ui/` … home / map / detail / edit / settings

## 次にやること（v0.2）
1. Overpass API による近隣一括取込。`osm_id` のUNIQUE制約で重複を防ぐ
2. 取込値は confidence 0.6。既存値が self_visit のときは上書きせず `pending_change` に積む
3. 写真管理とメニュー管理、ML Kit 日本語OCRでのメニュー取込
4. OSM `opening_hours` → 内部JSONへの変換。失敗したら `opening_hours_raw` に原文退避

## ビルド
push すると GitHub Actions が debug APK を Artifacts に出す。
