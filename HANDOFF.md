# MeshiHQApp HANDOFF

## これは何
近所の飲食店を自分のDBに貯めて管理するAndroidアプリ。オントロジー「飲食店マップ管理アプリ」の実装第一弾。

## 現在地点
v0.2（Overpass取込＋承認キュー＋opening_hours変換）。写真・メニュー・OCRは未実装。

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

## v0.2で入ったもの
- `data/remote/OverpassClient.kt` … Overpass APIへPOST。amenity=restaurant/cafe/fast_food/pub/bar/ice_cream かつ name 付きのみ取得
- `OsmCategory` … OSMの cuisine / amenity から固定16カテゴリへ写像
- `domain/OpeningHours.kt` … OSMの opening_hours を内部JSONへ変換（Mo-Fr 形式、複数スパン、off に対応）。失敗時は null を返し原文を raw に退避
- `ShopRepository.importFromOsm()` … 新規追加／自動更新／承認待ちの振り分け。既存店の同定は osm_id、無ければ同名かつ80m以内
- `ui/import_/ImportScreen.kt` … 取込タブ。半径選択、取込実行、承認待ちの承認・却下

### 更新判定の実装
値ごとに `fact_source` の最良信頼度を引き、OSMの0.6がそれ以上なら自動更新。下回れば `pending_change` に積む。
既存値が空のときは無条件で埋める。自分で入力した値（1.0）は必ず承認待ち経由になる。

## 次にやること（v0.3）
1. 写真管理（端末ギャラリー取込、店舗ひも付け）
2. メニュー管理と ML Kit 日本語OCRでのメニュー取込
3. 来店履歴・統計・コレクション
4. 取込のレート配慮（Overpassは連続実行を避ける。UI側で連打防止済みだが間隔の目安を入れる）

## ビルド
push すると GitHub Actions が debug APK を Artifacts に出す。
