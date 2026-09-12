import { InstanceBase, InstanceStatus, type SomeCompanionConfigField } from '@companion-module/base'
import WebSocket, { type RawData } from 'ws'
import { GetConfigFields, type ModuleConfig, type ModuleSecrets } from './config.js'
import { UpdateVariableDefinitions, type VariablesSchema } from './variables.js'
import { UpgradeScripts } from './upgrades.js'
import { UpdateActions, type ActionsSchema } from './actions.js'
import { UpdateFeedbacks, type FeedbacksSchema } from './feedbacks.js'
import { UpdatePresets } from './presets.js'
import { EmptyState, formatTime, type PotPlayerPlaybackState } from './state.js'

export type ModuleSchema = {
	config: ModuleConfig
	secrets: ModuleSecrets
	actions: ActionsSchema
	feedbacks: FeedbacksSchema
	variables: VariablesSchema
}

type IncomingMessage = {
	type?: string
	requestId?: string | null
	ok?: boolean
	error?: string | { message?: string }
	state?: PotPlayerPlaybackState | null
	folder?: string
	files?: unknown
}

export type MediaFile = { label: string; path: string }

export { UpgradeScripts }

export default class ModuleInstance extends InstanceBase<ModuleSchema> {
	config!: ModuleConfig
	secrets!: ModuleSecrets
	state: PotPlayerPlaybackState = { ...EmptyState }
	authenticated = false
	mediaFiles: MediaFile[] = []
	mediaFolder = ''

	private socket?: WebSocket
	private reconnectTimer?: NodeJS.Timeout
	private destroyed = false
	private requestCounter = 0

	constructor(internal: unknown) {
		super(internal)
	}

	async init(config: ModuleConfig, _isFirstInit: boolean, secrets: ModuleSecrets): Promise<void> {
		this.config = config
		this.secrets = secrets
		this.destroyed = false
		this.updateActions()
		this.updateFeedbacks()
		this.updatePresets()
		this.updateVariableDefinitions()
		this.setVariableValues({ media_folder: '', media_file_count: 0 })
		this.publishState()
		this.connect()
	}

	async destroy(): Promise<void> {
		this.destroyed = true
		this.clearReconnect()
		this.closeSocket()
	}

	async configUpdated(config: ModuleConfig, secrets: ModuleSecrets): Promise<void> {
		this.config = config
		this.secrets = secrets
		this.connect()
	}

	getConfigFields(): SomeCompanionConfigField[] {
		return GetConfigFields()
	}

	private buildUrl(): string {
		let host = String(this.config.host || '').trim()
		host = host.replace(/^wss?:\/\//i, '').replace(/\/.*$/, '')
		if (host.startsWith('[')) {
			const closing = host.indexOf(']')
			if (closing > 0) host = host.slice(1, closing)
		} else if (/^[^:]+:\d+$/.test(host)) {
			host = host.replace(/:\d+$/, '')
		}
		if (!host) throw new Error('請輸入 PotPlayer 主機位址')
		const port = Number(this.config.port || 19191)
		if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('連接埠不正確')
		return `ws://${host.includes(':') ? `[${host}]` : host}:${port}/ws`
	}

	private connect(): void {
		this.clearReconnect()
		this.closeSocket()
		this.authenticated = false
		this.publishConnection('connecting')

		if (!String(this.secrets?.token || '').trim()) {
			this.updateStatus(InstanceStatus.BadConfig, '請輸入 PotPlayer Bridge 的配對金鑰')
			this.publishConnection('missing_token')
			return
		}

		let url: string
		try {
			url = this.buildUrl()
		} catch (error) {
			this.updateStatus(InstanceStatus.BadConfig, String(error))
			this.publishConnection('bad_config')
			return
		}

		this.updateStatus(InstanceStatus.Connecting)
		this.log('debug', `Connecting to ${url}`)
		const socket = new WebSocket(url)
		this.socket = socket

		socket.on('open', () => {
			if (socket !== this.socket) return
			this.sendRaw({
				type: 'auth',
				token: this.secrets.token,
				requestId: this.nextRequestId('auth'),
			})
		})

		socket.on('message', (data: RawData) => this.handleMessage(data))
		socket.on('error', (error) => {
			if (socket !== this.socket) return
			this.log('warn', `WebSocket error: ${error.message}`)
		})
		socket.on('close', () => {
			if (socket !== this.socket) return
			this.socket = undefined
			this.authenticated = false
			this.updateStatus(InstanceStatus.ConnectionFailure, 'PotPlayer 連線中斷')
			this.publishConnection('disconnected')
			if (!this.destroyed) {
				this.reconnectTimer = setTimeout(() => this.connect(), 3000)
			}
		})
	}

	private closeSocket(): void {
		const socket = this.socket
		this.socket = undefined
		if (socket) {
			socket.removeAllListeners()
			try {
				socket.close()
			} catch (_error) {
				// Socket may not have completed its handshake yet.
			}
		}
	}

	private clearReconnect(): void {
		if (this.reconnectTimer) clearTimeout(this.reconnectTimer)
		this.reconnectTimer = undefined
	}

	private handleMessage(data: RawData): void {
		let message: IncomingMessage
		try {
			let text: string
			if (Array.isArray(data)) text = Buffer.concat(data).toString('utf8')
			else if (data instanceof ArrayBuffer) text = Buffer.from(data).toString('utf8')
			else text = data.toString('utf8')
			message = JSON.parse(text) as IncomingMessage
		} catch (error) {
			this.log('warn', `Invalid message from PotPlayer: ${String(error)}`)
			return
		}

		if (message.type === 'auth_result') {
			if (message.ok) {
				this.authenticated = true
				this.updateStatus(InstanceStatus.Ok)
				this.publishConnection('connected')
				if (message.state) this.applyState(message.state)
				this.sendRaw({ type: 'get_state', requestId: this.nextRequestId('state') })
				this.refreshLibrary()
			} else {
				this.authenticated = false
				this.updateStatus(InstanceStatus.AuthenticationFailure, '配對金鑰不正確')
				this.publishConnection('authentication_failed')
			}
			return
		}

		if (message.type === 'state' || message.type === 'command_result') {
			if (message.state) this.applyState(message.state)
			if (message.type === 'command_result' && message.ok === false) {
				const detail = typeof message.error === 'string' ? message.error : message.error?.message
				this.log('warn', `PotPlayer command failed: ${detail || 'Unknown error'}`)
			}
			return
		}

		if (message.type === 'library') {
			this.mediaFolder = typeof message.folder === 'string' ? message.folder : ''
			this.mediaFiles = Array.isArray(message.files)
				? message.files
						.filter(
							(value): value is MediaFile =>
								typeof value === 'object' &&
								value !== null &&
								typeof (value as MediaFile).label === 'string' &&
								typeof (value as MediaFile).path === 'string',
						)
						.slice(0, 2000)
				: []
			this.setVariableValues({
				media_folder: this.mediaFolder,
				media_file_count: this.mediaFiles.length,
			})
			this.updateActions()
			return
		}

		if (message.type === 'error') {
			const detail = typeof message.error === 'string' ? message.error : message.error?.message
			this.log('warn', `PotPlayer error: ${detail || 'Unknown error'}`)
		}
	}

	private applyState(state: PotPlayerPlaybackState): void {
		this.state = { ...EmptyState, ...state }
		this.publishState()
		this.checkAllFeedbacks()
	}

	private publishConnection(value: string): void {
		this.setVariableValues({ connection_status: value })
		this.checkFeedbacks('connected')
	}

	private publishState(): void {
		const state = this.state
		this.setVariableValues({
			playback: state.playback,
			paused: state.paused,
			idle: state.idle,
			title: state.title,
			url: state.url,
			position: state.position ?? 0,
			duration: state.duration ?? 0,
			remaining: state.remaining ?? 0,
			remaining_seconds: state.remainingSeconds ?? 0,
			playback_finished: state.playbackFinished,
			progress: state.progress ?? 0,
			position_time: formatTime(state.position),
			duration_time: formatTime(state.duration),
			remaining_time: formatTime(state.remaining),
			volume: state.volume,
			muted: state.muted,
			speed: state.speed,
			fullscreen: state.fullscreen,
			pip: state.pip,
			ontop: state.ontop,
			playlist_position: state.playlistPosition >= 0 ? state.playlistPosition + 1 : 0,
			playlist_count: state.playlistCount,
			chapter: state.chapter >= 0 ? state.chapter + 1 : 0,
			chapter_count: state.chapterCount,
			audio_track: state.audioTrack ?? 0,
			video_track: state.videoTrack ?? 0,
			subtitle_track: state.subtitleTrack ?? 0,
			second_subtitle_track: state.secondSubtitleTrack ?? 0,
			audio_delay: state.audioDelay,
			subtitle_delay: state.subtitleDelay,
			subtitle_visible: state.subtitleVisible,
			loop_file: state.loopFile,
			loop_playlist: state.loopPlaylist,
			filename: state.filename || state.title,
			video_info: state.videoInfo || '',
			end_behavior: state.endBehavior || 'hold',
		})
	}

	private nextRequestId(prefix: string): string {
		this.requestCounter += 1
		return `${prefix}-${Date.now()}-${this.requestCounter}`
	}

	private sendRaw(message: Record<string, unknown>): boolean {
		if (!this.socket || this.socket.readyState !== WebSocket.OPEN) return false
		this.socket.send(JSON.stringify(message))
		return true
	}

	sendCommand(command: string, args: Record<string, unknown> = {}): void {
		if (!this.authenticated) {
			this.log('warn', 'PotPlayer 尚未連線，無法送出指令')
			return
		}
		if (!this.sendRaw({ type: 'command', command, args, requestId: this.nextRequestId('command') })) {
			this.log('warn', 'PotPlayer WebSocket 尚未連線')
		}
	}

	refreshLibrary(): void {
		if (!this.authenticated) {
			this.log('warn', 'PotPlayer 尚未連線，無法更新媒體清單')
			return
		}
		this.sendRaw({ type: 'refresh_library', requestId: this.nextRequestId('library') })
	}

	updateActions(): void {
		UpdateActions(this)
	}

	updateFeedbacks(): void {
		UpdateFeedbacks(this)
	}

	updatePresets(): void {
		UpdatePresets(this)
	}

	updateVariableDefinitions(): void {
		UpdateVariableDefinitions(this)
	}
}
