import { ArrowLeftIcon } from '@phosphor-icons/react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { store } from '../api/store'
import type { Article } from '../api/types'
import { ErrorBanner, Pagination, Spinner, useTitle } from '../components/common'
import { formatDay } from '../format'
import { useAsync } from '../hooks'
import { useSeo } from '../seo'

const TYPES = [
  ['', 'Tất cả'],
  ['LOOKBOOK', 'Lookbook'],
  ['ARTICLE', 'Bài viết'],
] as const
const PAGE_SIZE = 12

/** Magazine: published articles and lookbooks, newest first, filterable by kind. */
export function ArticlesPage() {
  const [params, setParams] = useSearchParams()
  const type = (TYPES.find(([key]) => key === params.get('type'))?.[0] ?? '') as '' | 'ARTICLE' | 'LOOKBOOK'
  const page = Math.max(0, Number(params.get('page') || 1) - 1)
  const articles = useAsync(() => store.articles(type || undefined, PAGE_SIZE, page), [type, page])
  useTitle(type === 'LOOKBOOK' ? 'Lookbook' : 'Tạp chí')
  useSeo({ description: 'Gợi ý phối đồ, lookbook theo mùa và câu chuyện thời trang từ LemonadeX.' })

  return (
    <div className="container articles-page">
      <h1 className="page-title">Tạp chí</h1>
      <p className="muted page-lede">Gợi ý phối đồ, lookbook theo mùa và câu chuyện phía sau từng bộ sưu tập.</p>
      <div className="tabs" role="tablist" aria-label="Loại bài">
        {TYPES.map(([key, label]) => (
          <button key={key} type="button" role="tab" aria-selected={type === key} aria-pressed={type === key}
            onClick={() => setParams(key ? { type: key } : {})}>
            {label}
          </button>
        ))}
      </div>
      <ErrorBanner error={articles.error} onRetry={articles.reload} />
      {!articles.data && !articles.error ? (
        <Spinner />
      ) : articles.data && articles.data.content.length === 0 ? (
        <div className="empty-state">
          <h2>Chưa có bài viết</h2>
          <p className="muted">Bài mới sẽ sớm lên. Trong lúc chờ, xem các bộ sưu tập nhé.</p>
          <Link to="/shop/collections" className="btn btn-primary">
            Xem bộ sưu tập
          </Link>
        </div>
      ) : (
        articles.data && (
          <>
            <div className="article-grid">
              {articles.data.content.map((article, index) => (
                <Link key={article.id} to={`/shop/articles/${article.slug}`} className={`article-card${index === 0 && page === 0 ? ' article-lead' : ''}`}>
                  {article.thumbnailUrl ? <img src={article.thumbnailUrl} alt="" loading="lazy" /> : <span className="lookbook-empty" aria-hidden />}
                  <span className="article-kind">{article.articleType === 'LOOKBOOK' ? 'Lookbook' : 'Bài viết'}</span>
                  <h2>{article.title}</h2>
                  <span className="muted small">{formatDay(article.publishedAt)}</span>
                </Link>
              ))}
            </div>
            <Pagination
              page={page}
              totalPages={articles.data.totalPages}
              onChange={(next) => setParams({ ...(type ? { type } : {}), page: String(next + 1) })}
            />
          </>
        )
      )}
    </div>
  )
}

export function ArticlePage() {
  const { slug = '' } = useParams()
  const article = useAsync(() => store.article(slug), [slug])
  useTitle(article.data?.title)

  if (article.error) {
    return (
      <div className="container narrow">
        <ErrorBanner error={article.error} onRetry={article.reload} />
        <Link to="/shop/articles" className="text-link">
          Về trang tạp chí
        </Link>
      </div>
    )
  }
  if (!article.data) return <div className="container narrow"><Spinner /></div>
  return <ArticleView article={article.data} />
}

function ArticleView({ article }: { article: Article }) {
  const text = article.content.replace(/<[^>]+>/g, ' ')
  useSeo({ description: text.slice(0, 200), image: article.thumbnailUrl ?? article.images[0], type: 'article', jsonLd: articleJsonLd(article, text) })
  const related = useAsync(() => store.articles(article.articleType, 4), [article.articleType])
  const more = (related.data?.content ?? []).filter((a) => a.id !== article.id).slice(0, 3)

  return (
    <article className="container narrow article-page">
      <Link to="/shop/articles" className="back-link">
        <ArrowLeftIcon size={14} /> Tạp chí
      </Link>
      <header className="article-head">
        <span className="article-kind">{article.articleType === 'LOOKBOOK' ? 'Lookbook' : 'Bài viết'}</span>
        <h1 className="page-title">{article.title}</h1>
        <p className="muted">{formatDay(article.publishedAt)}</p>
      </header>
      {article.thumbnailUrl && <img className="article-cover" src={article.thumbnailUrl} alt="" />}
      <div className="prose article-body">{article.content}</div>
      {article.images.length > 0 && (
        <div className={`article-gallery${article.articleType === 'LOOKBOOK' ? ' lookbook' : ''}`}>
          {article.images.map((src) => (
            <img key={src} src={src} alt="" loading="lazy" />
          ))}
        </div>
      )}
      <div className="article-cta">
        <Link to="/shop/products?sort=newest" className="btn btn-primary">
          Mua sắm hàng mới
        </Link>
      </div>
      {more.length > 0 && (
        <section className="section" aria-labelledby="more-title">
          <h2 id="more-title" className="section-title">
            Đọc thêm
          </h2>
          <div className="article-grid compact">
            {more.map((a) => (
              <Link key={a.id} to={`/shop/articles/${a.slug}`} className="article-card">
                {a.thumbnailUrl ? <img src={a.thumbnailUrl} alt="" loading="lazy" /> : <span className="lookbook-empty" aria-hidden />}
                <h3>{a.title}</h3>
                <span className="muted small">{formatDay(a.publishedAt)}</span>
              </Link>
            ))}
          </div>
        </section>
      )}
    </article>
  )
}

/** schema.org Article, so search results can show the headline, image and date. */
function articleJsonLd(article: Article, text: string) {
  return {
    '@context': 'https://schema.org',
    '@type': 'Article',
    headline: article.title,
    description: text.replace(/\s+/g, ' ').trim().slice(0, 200),
    image: [article.thumbnailUrl, ...article.images].filter(Boolean),
    datePublished: article.publishedAt,
    dateModified: article.updatedAt,
    publisher: { '@type': 'Organization', name: 'LemonadeX' },
    mainEntityOfPage: window.location.origin + '/shop/articles/' + article.slug,
  }
}
