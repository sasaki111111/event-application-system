// 実行環境: ブラウザ側（テスト実行時はNode.js上でVitest／jsdomにより再現）。comment-api.tsの単体テスト。
// buildCommentTree()は返信の無制限階層をフラットなAPIレスポンスから組み立てる純粋な計算ロジックであり、
// 壊れるとコメント欄全体の表示が崩れるため重点的に検証する。
import { buildCommentTree, EventComment } from './comment-api';

// 各テストで共通のコメント1件分を作るヘルパー。overridesで必要なフィールドだけ上書きできる
// （TypeScriptのPartial<T>: T型のすべてのプロパティを省略可能にした型）
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
    // parentCommentIdを指定しない（=null）コメントを2件用意する
    const comments = [makeComment({ id: 1 }), makeComment({ id: 2 })];

    // テスト対象の関数を実行する
    const tree = buildCommentTree(comments);

    // 2件とも親が無いので、ルート（配列の要素）が2つになる
    expect(tree).toHaveLength(2);
    // 返信（children）はどちらも0件のはず
    expect(tree[0].children).toHaveLength(0);
  });

  it('parentCommentIdで指定した親の子として組み立てる', () => {
    // id:2のコメントがid:1への返信（parentCommentId: 1）になっている
    const comments = [
      makeComment({ id: 1 }),
      makeComment({ id: 2, parentCommentId: 1 }),
    ];

    const tree = buildCommentTree(comments);

    // ルートはid:1の1件だけ（id:2は返信としてまとめられ、ルートには現れない）
    expect(tree).toHaveLength(1);
    expect(tree[0].comment.id).toBe(1);
    // id:1のchildrenにid:2が1件入っていること
    expect(tree[0].children).toHaveLength(1);
    expect(tree[0].children[0].comment.id).toBe(2);
  });

  it('返信の階層数に制限が無く、何階層でも入れ子にできる', () => {
    // 1→2→3→4と、返信の返信の返信…という4階層の鎖を作る
    const comments = [
      makeComment({ id: 1 }),
      makeComment({ id: 2, parentCommentId: 1 }),
      makeComment({ id: 3, parentCommentId: 2 }),
      makeComment({ id: 4, parentCommentId: 3 }),
    ];

    const tree = buildCommentTree(comments);

    // children.children.children...とたどって、4階層目のid:4まで正しく入れ子になっていることを確認する
    expect(tree[0].children[0].children[0].children[0].comment.id).toBe(4);
  });

  it('1つの親に複数の返信がある場合、すべて子として並ぶ', () => {
    // id:2・id:3の両方がid:1への返信になっている
    const comments = [
      makeComment({ id: 1 }),
      makeComment({ id: 2, parentCommentId: 1 }),
      makeComment({ id: 3, parentCommentId: 1 }),
    ];

    const tree = buildCommentTree(comments);

    // 2件とも同じ親のchildrenに、投稿順（APIが返す順）のまま並ぶことを確認する
    expect(tree[0].children.map((c) => c.comment.id)).toEqual([2, 3]);
  });

  it('削除済み（deleted: true）のコメントも、返信の親としてそのままツリーに残る', () => {
    // id:1は論理削除済みだが、id:2がそれへの返信として存在する
    const comments = [
      makeComment({ id: 1, deleted: true, body: 'このコメントは削除されました' }),
      makeComment({ id: 2, parentCommentId: 1 }),
    ];

    const tree = buildCommentTree(comments);

    // 削除済みでもツリーからは除外されず、返信の親として残ることを確認する
    expect(tree[0].comment.deleted).toBe(true);
    expect(tree[0].children).toHaveLength(1);
  });
});
