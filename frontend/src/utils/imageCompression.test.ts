import { describe, it, expect, vi, afterEach } from 'vitest'
import { compressImage } from './imageCompression'

function makeFile(bytes: number, type = 'image/jpeg', name = 'photo.jpg'): File {
  return new File([new Uint8Array(bytes)], name, { type })
}

describe('compressImage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('returns the original file when it is already small', async () => {
    const file = makeFile(100 * 1024)
    const result = await compressImage(file)
    expect(result).toBe(file)
  })

  it('returns the original file for non-image types', async () => {
    const file = makeFile(500 * 1024, 'application/pdf', 'doc.pdf')
    const result = await compressImage(file)
    expect(result).toBe(file)
  })

  it('compresses a large photo into a smaller jpeg', async () => {
    const file = makeFile(500 * 1024)

    class FakeImage {
      width = 4000
      height = 3000
      onload: (() => void) | null = null
      onerror: (() => void) | null = null
      set src(_value: string) {
        setTimeout(() => this.onload?.(), 0)
      }
    }
    vi.stubGlobal('Image', FakeImage as unknown as typeof Image)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})

    const smallBlob = new Blob([new Uint8Array(50 * 1024)], { type: 'image/jpeg' })
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage: vi.fn(),
    } as unknown as CanvasRenderingContext2D)
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback: BlobCallback) => {
      callback(smallBlob)
    })

    const result = await compressImage(file)

    expect(result).not.toBe(file)
    expect(result.type).toBe('image/jpeg')
    expect(result.size).toBe(smallBlob.size)
    expect(result.name).toBe('photo.jpg')
  })

  it('falls back to the original file when the compressed result is not smaller', async () => {
    const file = makeFile(500 * 1024)

    class FakeImage {
      width = 4000
      height = 3000
      onload: (() => void) | null = null
      onerror: (() => void) | null = null
      set src(_value: string) {
        setTimeout(() => this.onload?.(), 0)
      }
    }
    vi.stubGlobal('Image', FakeImage as unknown as typeof Image)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})

    const biggerBlob = new Blob([new Uint8Array(600 * 1024)], { type: 'image/jpeg' })
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage: vi.fn(),
    } as unknown as CanvasRenderingContext2D)
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback: BlobCallback) => {
      callback(biggerBlob)
    })

    const result = await compressImage(file)

    expect(result).toBe(file)
  })
})
