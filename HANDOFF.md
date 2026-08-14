# MeshiHQApp HANDOFF

## これは何
近所の飲食店を自分のDBに貯めて管理するAndroidアプリ。オントロジー「飲食店マップ管理アプリ」の実装第一弾。

## 現在地点
v0.4（来店履歴・統計・コレクション・メニュー重複整理まで）。AI検索／おすすめは未実装。

## パッケージ名について
applicationId は `jp.appathy.meshihq2`（namespace は `jp.appathy.meshihq` のまま）。
`jp.appathy.meshihq` は端末側に残骸があるらしくインストールが通らなかったため、恒久的にこちらを使う。戻さないこと。

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

## v0.3で入ったもの
- DBスキーマ v2。`photo` と `menu_item` を追加。`MIGRATION_1_2` を手書きで用意しているので既存データは残る
- `data/media/PhotoStore.kt` … ギャラリーで選んだ画像を長辺1600pxに縮小してアプリ内へコピー。URI権限切れを避けるため実体を持つ
- `data/ocr/MenuOcr.kt` … ML Kit 日本語モデル（端末内完結、通信なし）で行を読み、`〜円` や `¥〜` から価格を、残りを品名として候補化
- 店舗詳細のタブが 情報／メニュー／写真／根拠 の4枚に
- Bonsai資料のMarkdownに「## メニュー」節を追加。RAG側で品名と価格が引ける

### 出所種別に ocr を追加
信頼度0.7。手入力（1.0）より下、OSM（0.6）より上。

## v0.4で入ったもの
- DBスキーマ v3。`visit` / `collection` / `collection_shop` を追加（`MIGRATION_2_3` あり）
- 店舗詳細に来店タブ。日付は記録時点、人数・支払い・評価1〜5・メモ。合計と1人あたり平均を表示
- 来店を記録すると、状態が「未確認」の店は自動で「営業中」に上がる
- 記録タブ（ボトムナビ5枚目）… カテゴリ別／月別来店／よく行く店／コレクション管理
- コレクションへの登録は店舗詳細の情報タブのチップから
- メニュー追加時に同名かつ同価格を弾き、「重複を整理」で同名を1件へ畳む（価格入りを優先して残す）

## 次にやること（v0.5）
1. AI検索・おすすめ（BonsaiAppクライアント）。着手前に BONSAI_API.md をこのリポジトリに置くこと。
   契約が手元にない状態でエンドポイントを推測して実装しない
2. 統計の期間フィルタ（今月／今年／全期間）
3. 写真のEXIF撮影日時を来店記録の候補にする
4. 取込のレート配慮（Overpassの連続実行を避ける間隔表示）

## ビルド・署名
push すると GitHub Actions が release APK（未minify）を Artifacts に出す。

署名鍵は `keystore/meshihq.jks` をリポジトリに同梱し、debug/release とも同じ鍵で署名する（storePassword/keyPassword/alias すべて `meshihq`）。
CIランナーの debug.keystore は実行ごとに作り直されるため、それに任せると毎回署名が変わり、上書きインストールが
「INSTALL_FAILED_UPDATE_INCOMPATIBLE（署名が一致しません）」で失敗する。鍵を固定することでこれを回避している。
配布用の鍵ではないので、ストア公開する段階になったら別の鍵に差し替えること。
