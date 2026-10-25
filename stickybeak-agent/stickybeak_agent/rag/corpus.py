"""售后知识库语料：knowledge/{zh,en}/*.md，按二级标题切分。

一个二级标题下的内容就是一个完整的知识点（例如"退货条件"），通常几百字，直接作为一个片段；
超过 MAX_CHARS 时按段落再切，并把标题拼在每个子片段开头，保证片段脱离上下文也能读懂。
片段 ID 由文件、标题、序号决定，内容哈希用于增量入库（内容没变就不重新向量化）。
"""

from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from pathlib import Path

MAX_CHARS = 700
KNOWLEDGE_DIR = Path(__file__).resolve().parents[2] / "knowledge"


@dataclass(frozen=True)
class Chunk:
    id: str
    doc_id: str
    lang: str
    title: str
    section: str
    text: str
    source: str

    @property
    def content_hash(self) -> str:
        return hashlib.sha1(f"{self.title}\n{self.section}\n{self.text}".encode()).hexdigest()

    def embedding_input(self) -> str:
        return f"{self.title} - {self.section}\n{self.text}"


def load_corpus(root: Path = KNOWLEDGE_DIR) -> list[Chunk]:
    chunks: list[Chunk] = []
    for path in sorted(root.glob("*/*.md")):
        lang = path.parent.name
        chunks.extend(
            split_markdown(path.read_text(encoding="utf-8"), doc_id=path.stem, lang=lang, source=f"{lang}/{path.name}")
        )
    return chunks


def split_markdown(markdown: str, *, doc_id: str, lang: str, source: str) -> list[Chunk]:
    title_match = re.search(r"^#\s+(.+)$", markdown, re.MULTILINE)
    title = title_match.group(1).strip() if title_match else doc_id
    chunks: list[Chunk] = []
    # 以二级标题切分；第一个二级标题之前的导语也算一个片段
    parts = re.split(r"^##\s+(.+)$", markdown, flags=re.MULTILINE)
    intro = re.sub(r"^#\s+.+$", "", parts[0], flags=re.MULTILINE).strip()
    sections: list[tuple[str, str]] = [(title, intro)] if intro else []
    sections += [(parts[i].strip(), parts[i + 1].strip()) for i in range(1, len(parts) - 1, 2)]

    for s_idx, (section, body) in enumerate(sections):
        for p_idx, text in enumerate(_split_long(body)):
            chunk_id = hashlib.sha1(f"{source}#{s_idx}#{p_idx}".encode()).hexdigest()[:20]
            chunks.append(Chunk(chunk_id, doc_id, lang, title, section, text, source))
    return chunks


def _split_long(body: str) -> list[str]:
    if len(body) <= MAX_CHARS:
        return [body] if body else []
    pieces: list[str] = []
    current = ""
    for para in re.split(r"\n\s*\n", body):
        para = para.strip()
        if current and len(current) + len(para) + 2 > MAX_CHARS:
            pieces.append(current)
            current = para
        else:
            current = f"{current}\n\n{para}" if current else para
    if current:
        pieces.append(current)
    return pieces
