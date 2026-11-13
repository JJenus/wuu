import { handleAccountMutation } from '../../utils/account-request'

export default defineEventHandler((event) => handleAccountMutation(event, 'profile'))
