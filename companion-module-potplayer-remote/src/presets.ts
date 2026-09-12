import { combineRgb, type CompanionPresetDefinitions, type CompanionPresetSection } from '@companion-module/base'
import type ModuleInstance from './main.js'
import type { ModuleSchema } from './main.js'

const white = combineRgb(255, 255, 255)
const black = combineRgb(0, 0, 0)
const dark = combineRgb(25, 25, 25)

export function UpdatePresets(self: ModuleInstance): void {
	const presets: CompanionPresetDefinitions<ModuleSchema> = {
		play: {
			type: 'simple',
			name: '播放',
			style: { text: '▶\n播放', size: 'auto', color: white, bgcolor: dark, show_topbar: false },
			steps: [{ down: [{ actionId: 'transport', options: { operation: 'play' } }], up: [] }],
			feedbacks: [{ feedbackId: 'is_playing', options: {}, style: { color: white, bgcolor: combineRgb(0, 150, 70) } }],
		},
		pause: {
			type: 'simple',
			name: '暫停',
			style: { text: 'Ⅱ\n暫停', size: 'auto', color: white, bgcolor: dark, show_topbar: false },
			steps: [{ down: [{ actionId: 'transport', options: { operation: 'pause' } }], up: [] }],
			feedbacks: [{ feedbackId: 'is_paused', options: {}, style: { color: black, bgcolor: combineRgb(255, 190, 0) } }],
		},
		toggle: {
			type: 'simple',
			name: '播放／暫停',
			style: { text: '▶ / Ⅱ', size: 'auto', color: white, bgcolor: dark, show_topbar: false },
			steps: [{ down: [{ actionId: 'transport', options: { operation: 'toggle_play_pause' } }], up: [] }],
			feedbacks: [
				{ feedbackId: 'is_playing', options: {}, style: { color: white, bgcolor: combineRgb(0, 150, 70) } },
				{ feedbackId: 'is_paused', options: {}, style: { color: black, bgcolor: combineRgb(255, 190, 0) } },
			],
		},
		stop: {
			type: 'simple',
			name: '停止',
			style: { text: '■\n停止', size: 'auto', color: white, bgcolor: combineRgb(150, 0, 0), show_topbar: false },
			steps: [{ down: [{ actionId: 'transport', options: { operation: 'stop' } }], up: [] }],
			feedbacks: [],
		},
		seek_back_10: {
			type: 'simple',
			name: '後退 10 秒',
			style: { text: '↶\n10 秒', size: 'auto', color: white, bgcolor: dark, show_topbar: false },
			steps: [{ down: [{ actionId: 'seek_relative', options: { seconds: -10, exact: false } }], up: [] }],
			feedbacks: [],
		},
		seek_forward_10: {
			type: 'simple',
			name: '前進 10 秒',
			style: { text: '↷\n10 秒', size: 'auto', color: white, bgcolor: dark, show_topbar: false },
			steps: [{ down: [{ actionId: 'seek_relative', options: { seconds: 10, exact: false } }], up: [] }],
			feedbacks: [],
		},
		previous: {
			type: 'simple',
			name: '上一個',
			style: { text: '⏮\n上一個', size: 'auto', color: white, bgcolor: dark, show_topbar: false },
			steps: [{ down: [{ actionId: 'playlist', options: { operation: 'playlist_previous' } }], up: [] }],
			feedbacks: [],
		},
		next: {
			type: 'simple',
			name: '下一個',
			style: { text: '⏭\n下一個', size: 'auto', color: white, bgcolor: dark, show_topbar: false },
			steps: [{ down: [{ actionId: 'playlist', options: { operation: 'playlist_next' } }], up: [] }],
			feedbacks: [],
		},
		mute: {
			type: 'simple',
			name: '靜音切換',
			style: { text: '🔇\n靜音', size: 'auto', color: white, bgcolor: dark, show_topbar: false },
			steps: [{ down: [{ actionId: 'mute', options: { operation: 'toggle' } }], up: [] }],
			feedbacks: [{ feedbackId: 'muted', options: {}, style: { color: white, bgcolor: combineRgb(190, 0, 0) } }],
		},
		fullscreen: {
			type: 'simple',
			name: '全螢幕切換',
			style: { text: '⛶\n全螢幕', size: 'auto', color: white, bgcolor: dark, show_topbar: false },
			steps: [{ down: [{ actionId: 'fullscreen', options: { operation: 'toggle' } }], up: [] }],
			feedbacks: [{ feedbackId: 'fullscreen', options: {}, style: { color: white, bgcolor: combineRgb(0, 90, 180) } }],
		},
		fullscreen_screen_2: {
			type: 'simple',
			name: '第二螢幕全螢幕',
			style: { text: '⛶\n螢幕 2', size: 'auto', color: white, bgcolor: combineRgb(0, 70, 145), show_topbar: false },
			steps: [{ down: [{ actionId: 'fullscreen_on_screen', options: { screen: '2' } }], up: [] }],
			feedbacks: [{ feedbackId: 'fullscreen', options: {}, style: { color: white, bgcolor: combineRgb(0, 120, 210) } }],
		},
		refresh_library: {
			type: 'simple',
			name: '更新媒體清單',
			style: { text: '↻\n媒體清單', size: 'auto', color: white, bgcolor: dark, show_topbar: false },
			steps: [{ down: [{ actionId: 'refresh_library', options: {} }], up: [] }],
			feedbacks: [],
		},
		auto_close_on_end: {
			type: 'simple',
			name: '啟用播完關閉視窗',
			style: {
				text: '播完\n關閉視窗',
				size: 'auto',
				color: white,
				bgcolor: combineRgb(130, 40, 0),
				show_topbar: false,
			},
			steps: [{ down: [{ actionId: 'auto_close_on_end', options: { enabled: true } }], up: [] }],
			feedbacks: [],
		},
		countdown: {
			type: 'simple',
			name: '剩餘秒數',
			style: {
				text: '剩餘\n$(potplayer-remote:remaining_seconds) 秒',
				size: 'auto',
				color: white,
				bgcolor: dark,
				show_topbar: false,
			},
			steps: [],
			feedbacks: [
				{ feedbackId: 'playback_finished', options: {}, style: { color: white, bgcolor: combineRgb(180, 0, 0) } },
			],
		},
		status: {
			type: 'simple',
			name: '播放時間',
			style: {
				text: '$(potplayer-remote:position_time)\n$(potplayer-remote:duration_time)',
				size: 'auto',
				color: white,
				bgcolor: dark,
				show_topbar: false,
			},
			steps: [],
			feedbacks: [
				{ feedbackId: 'is_playing', options: {}, style: { color: white, bgcolor: combineRgb(0, 100, 55) } },
				{ feedbackId: 'is_paused', options: {}, style: { color: black, bgcolor: combineRgb(255, 190, 0) } },
			],
		},
	}

	const mediaPresetIds: string[] = []
	self.mediaFiles.forEach((file, index) => {
		const id = `media_file_${index}`
		mediaPresetIds.push(id)
		presets[id] = {
			type: 'simple',
			name: `播放：${file.label}`,
			style: { text: file.label, size: 'auto', color: white, bgcolor: combineRgb(0, 90, 55), show_topbar: false },
			steps: [
				{ down: [{ actionId: 'play_media_file', options: { path: file.path, startFromBeginning: true } }], up: [] },
			],
			feedbacks: [],
		}
	})

	const structure: CompanionPresetSection[] = [
		{
			id: 'transport',
			name: '播放控制',
			definitions: ['play', 'pause', 'toggle', 'stop', 'seek_back_10', 'seek_forward_10', 'previous', 'next'],
		},
		{
			id: 'display',
			name: '狀態與顯示',
			definitions: [
				'status',
				'countdown',
				'mute',
				'fullscreen',
				'fullscreen_screen_2',
				'auto_close_on_end',
				'refresh_library',
			],
		},
	]
	if (mediaPresetIds.length > 0) {
		structure.unshift({ id: 'media_files', name: '媒體檔案（按鈕顯示檔名）', definitions: mediaPresetIds })
	}

	self.setPresetDefinitions(structure, presets)
}
