// 実行環境: ブラウザ側（テスト実行時はNode.js上でVitest／jsdomにより再現）。comment-api.tsの単体テスト（D-17・D-18）。
// buildCommentTree()は返信の無制限階層をフラットなAPIレスポンスから組み立てる純粋な計算ロジックであり、
// 壊れるとコメント欄全体の表示が崩れるため重点的に検証する。
import { buildCommentTree, EventComment } from './comment-api';

function makeComment(overrides: Partial<EventComment>): EventComment {
  return {
    id: 1,
    userName: '利用者',
    body: '本文',
    createdAt: '2027-01-01T10:00:00',
    mine: false,
    parentCommentId: null,
    deleted: false,
    ...overrides,
  };
}

describe('buildCommentTree', () => {
  it('親を持たないコメントはすべてルートになる', () => {
    const comments = [makeComment({ id: 1 }), makeComment({ id: 2 })];

    const tree = buildCommentTree(comments);

    expect(tree).toHaveLength(2);
    expect(tree[0].children).toHaveLength(0);
  });

  it('parentCommentIdで指定した親の子として組み立てる', () => {
    const comments = [
      makeComment({ id: 1 }),
      makeComment({ id: 2, parentCommentId: 1 }),
    ];

    const tree = buildCommentTree(comments);

    expect(tree).toHaveLength(1);
    expect(tree[0].comment.id).toBe(1);
    expect(tree[0].children).toHaveLength(1);
    expect(tree[0].children[0].comment.id).toBe(2);
  });

  it('返信の階層数に制限が無く、何階層でも入れ子にできる', () => {
    const comments = [
      makeComment({ id: 1 }),
      makeComment({ id: 2, parentCommentId: 1 }),
      makeComment({ id: 3, parentCommentId: 2 }),
      makeComment({ id: 4, parentCommentId: 3 }),
    ];

    const tree = buildCommentTree(comments);

    expect(tree[0].children[0].children[0].children[0].comment.id).toBe(4);
  });

  it('1つの親に複数の返信がある場合、すべて子として並ぶ', () => {
    const comments = [
      makeComment({ id: 1 }),
      makeComment({ id: 2, parentCommentId: 1 }),
      makeComment({ id: 3, parentCommentId: 1 }),
    ];

    const tree = buildCommentTree(comments);

    expect(tree[0].children.map((c) => c.comment.id)).toEqual([2, 3]);
  });

  it('削除済み（deleted: true）のコメントも、返信の親としてそのままツリーに残る', () => {
    const comments = [
      makeComment({ id: 1, deleted: true, body: 'このコメントは削除されました' }),
      makeComment({ id: 2, parentCommentId: 1 }),
    ];

    const tree = buildCommentTree(comments);

    expect(tree[0].comment.deleted).toBe(true);
    expect(tree[0].children).toHaveLength(1);
  });
});
