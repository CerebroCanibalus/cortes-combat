"""
Convierte los OGG del mod de stereo a mono.

Por cada archivo:
  1. Lee con soundfile (memoria).
  2. Si es mono, lo deja igual.
  3. Si es stereo, hace downmix mono = 0.5 * (L + R) con clip.
  4. Escribe a <name>.tmp en OGG Vorbis, mismo sample rate.
  5. Verifica: re-lee el .tmp y exige channels == 1, frames iguales al original.
  6. Solo si todo OK, os.replace(tmp, original).
"""

from __future__ import annotations

import sys
from pathlib import Path
import numpy as np
import soundfile as sf

SOUNDS_DIR = Path(r"D:\Modding\Neoforge\cortes-combat\src\main\resources\assets\cortescombat\sounds")


def downmix_to_mono(data: np.ndarray) -> np.ndarray:
    """Mezcla (n, 2) -> (n,). Aplica clip [-1, 1] por seguridad."""
    if data.ndim == 1:
        return data
    if data.ndim != 2 or data.shape[1] != 2:
        raise ValueError(f"Forma inesperada: {data.shape}")
    mono = 0.5 * (data[:, 0] + data[:, 1])
    # Clip numérico. SFX de combate rara vez satura, pero el downmix puede empujar picos.
    np.clip(mono, -1.0, 1.0, out=mono)
    return mono.astype(np.float32, copy=False)


def process_one(path: Path) -> tuple[str, str]:
    """
    Procesa un archivo. Devuelve (estado, detalle).
    Estados: 'mono', 'stereo->mono', 'error', 'skip'.
    """
    info = sf.info(path)
    sr = info.samplerate
    frames = info.frames
    channels = info.channels

    if channels == 1:
        return ("mono", f"channels=1, sr={sr}, frames={frames}")

    # Leer en float32 para no inflar memoria.
    data, sr_read = sf.read(path, dtype="float32", always_2d=False)
    if sr_read != sr:
        return ("error", f"sr leído {sr_read} != info {sr}")
    if data.ndim != 2 or data.shape[1] != 2:
        return ("error", f"forma inesperada {data.shape}")

    mono = downmix_to_mono(data)

    tmp = path.with_suffix(path.suffix + ".tmp")
    # OGG/Vorbis con libsndfile no acepta cualquier dtype; pasamos float32 y soundfile
    # internamente convierte a PCM para Vorbis.
    sf.write(tmp, mono, sr, format="OGG", subtype="VORBIS")

    # Verificación: re-leer el .tmp
    info2 = sf.info(tmp)
    if info2.channels != 1:
        tmp.unlink(missing_ok=True)
        return ("error", f"verificación: channels={info2.channels} != 1")
    if info2.frames != frames:
        tmp.unlink(missing_ok=True)
        return ("error", f"verificación: frames {info2.frames} != {frames}")
    if info2.samplerate != sr:
        tmp.unlink(missing_ok=True)
        return ("error", f"verificación: sr {info2.samplerate} != {sr}")

    # OK -> reemplazar
    tmp.replace(path)
    return ("stereo->mono", f"ch=2->1, sr={sr}, frames={frames}")


def main() -> int:
    if not SOUNDS_DIR.is_dir():
        print(f"ERROR: no existe {SOUNDS_DIR}", file=sys.stderr)
        return 2

    oggs = sorted(SOUNDS_DIR.rglob("*.ogg"))
    if not oggs:
        print(f"ERROR: no hay .ogg en {SOUNDS_DIR}", file=sys.stderr)
        return 2

    n_mono = n_conv = n_err = 0
    for path in oggs:
        try:
            state, detail = process_one(path)
        except Exception as exc:
            state, detail = "error", f"{type(exc).__name__}: {exc}"
        rel = path.relative_to(SOUNDS_DIR.parent.parent.parent.parent.parent) if False else path.relative_to(SOUNDS_DIR)
        print(f"[{state:>13}] {rel}  --  {detail}")
        if state == "mono":
            n_mono += 1
        elif state == "stereo->mono":
            n_conv += 1
        else:
            n_err += 1

    print(f"\nResumen: convertidos={n_conv}, ya_mono={n_mono}, errores={n_err}, total={len(oggs)}")
    return 0 if n_err == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())